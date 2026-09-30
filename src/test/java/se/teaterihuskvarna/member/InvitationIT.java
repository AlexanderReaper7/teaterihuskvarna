package se.teaterihuskvarna.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/// Proves the invitation rules (R019): an administrator, or a household member
/// with an account, invites a member without one; the mailed link works once,
/// for a limited time, and creates the account without logging anyone in; an
/// address another account has is refused.
///
/// Erik has an account, Olle is in his household without one, and Ingrid is in
/// no household and has no account.
class InvitationIT extends MemberRegisterSupport {

    private static final String PATH = "/inbjudan";
    private static final String ERIK_EMAIL = "erik@example.test";
    private static final String OLLE_EMAIL = "olle@example.test";

    @Autowired
    private ExpiredInvitations expiredInvitations;

    private long erikAccount;
    private long olle;
    private long ingrid;

    @BeforeEach
    void household() {
        long household = insertHousehold("Familjen Lindqvist");
        erikAccount = insertAccount("Erik Lindqvist", ERIK_EMAIL);
        olle = insertMember("Olle Lindqvist");
        ingrid = insertMember("Ingrid Nyström");
        moveTo(memberOf(erikAccount), household);
        moveTo(olle, household);
    }

    /// Only the hash of the token is stored, and opening the link, as a mail
    /// scanner does, creates nothing.
    @Test
    void anAdministratorInvitesAndTheLinkCreatesTheAccount() throws Exception {
        adminInvites(olle, OLLE_EMAIL).andExpect(status().isAccepted());
        SimpleMailMessage mail = awaitMail();
        assertThat(mail.getTo()).containsExactly(OLLE_EMAIL);
        assertThat(mail.getText()).contains("Olle Lindqvist").contains("7 dygn");
        String token = tokenIn(mail, PATH);
        assertThat(jdbc.sql("SELECT token_hash FROM invitation").query(String.class).single())
                .isEqualTo(sha256Hex(token));

        mockMvc.perform(get(PATH).param("token", token))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Skapa konto")));
        assertThat(accountOf(olle)).isZero();

        mockMvc.perform(post(PATH).param("token", token).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ditt konto är skapat")));
        assertThat(jdbc.sql("SELECT email FROM account WHERE member_id = ?").param(olle).query(String.class)
                .single()).isEqualTo(OLLE_EMAIL);
        assertThat(rowsIn("invitation")).isZero();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM spring_session WHERE principal_name IS NOT NULL")
                .query(Long.class).single()).as("nobody is logged in").isZero();
    }

    @Test
    void aLinkWorksOnce() throws Exception {
        adminInvites(olle, OLLE_EMAIL);
        String token = tokenIn(awaitMail(), PATH);

        accept(token).andExpect(status().isNoContent());
        accept(token).andExpect(status().isNotFound());
        mockMvc.perform(post(PATH).param("token", token).with(csrf()))
                .andExpect(content().string(containsString("Länken fungerar inte")));
        assertThat(rowsIn("account")).isEqualTo(2);
    }

    @Test
    void anExpiredLinkCreatesNothingAndTheJobDeletesIt() throws Exception {
        adminInvites(olle, OLLE_EMAIL);
        String token = tokenIn(awaitMail(), PATH);
        jdbc.sql("UPDATE invitation SET expires_at = now() - interval '1 second'").update();

        expiredInvitations.delete();
        assertThat(rowsIn("invitation")).isZero();

        accept(token).andExpect(status().isNotFound());
        assertThat(accountOf(olle)).isZero();
    }

    @Test
    void anExpiredLinkIsRefusedBeforeTheJobRuns() throws Exception {
        adminInvites(olle, OLLE_EMAIL);
        String token = tokenIn(awaitMail(), PATH);
        jdbc.sql("UPDATE invitation SET expires_at = now() - interval '1 second'").update();

        mockMvc.perform(post(PATH).param("token", token).with(csrf()))
                .andExpect(content().string(containsString("Länken fungerar inte")));
        assertThat(accountOf(olle)).isZero();
    }

    /// Sending again replaces the invitation, so the older link stops working.
    @Test
    void resendingReplacesTheLink() throws Exception {
        adminInvites(olle, OLLE_EMAIL);
        String first = tokenIn(awaitMail(), PATH);
        forgetMails();
        adminInvites(olle, "olle2@example.test").andExpect(status().isAccepted());
        String second = tokenIn(awaitMail(), PATH);

        assertThat(rowsIn("invitation")).isEqualTo(1);
        accept(first).andExpect(status().isNotFound());
        accept(second).andExpect(status().isNoContent());
        assertThat(jdbc.sql("SELECT email FROM account WHERE member_id = ?").param(olle).query(String.class)
                .single()).isEqualTo("olle2@example.test");
    }

    @Test
    void anAddressAnotherAccountHasIsRefused() throws Exception {
        adminInvites(olle, "ERIK@example.test")
                .andExpect(status().isConflict());
        mockMvc.perform(post("/admin/medlemmar/" + olle + "/inbjudan")
                        .param("email", ERIK_EMAIL)
                        .with(asAdministrator())
                        .with(csrf()))
                .andExpect(flash().attribute("error",
                        "Adressen används redan av ett annat konto. Välj en annan adress."));
        assertNoMail();
        assertThat(rowsIn("invitation")).isZero();
    }

    /// The address was free when the invitation went out and taken before the
    /// link was used.
    @Test
    void anAddressTakenMeanwhileCreatesNoAccount() throws Exception {
        adminInvites(olle, OLLE_EMAIL);
        String token = tokenIn(awaitMail(), PATH);
        insertAccount("Någon annan", "Olle@example.test");

        mockMvc.perform(post(PATH).param("token", token).with(csrf()))
                .andExpect(content().string(containsString("Adressen används redan")));
        assertThat(accountOf(olle)).isZero();

        forgetMails();
        adminInvites(ingrid, "ingrid@example.test");
        String other = tokenIn(awaitMail(), PATH);
        insertAccount("Ännu en", "ingrid@example.test");
        accept(other).andExpect(status().isConflict());
    }

    @Test
    void aMemberWithAnAccountIsNotInvited() throws Exception {
        adminInvites(memberOf(erikAccount), "erik2@example.test").andExpect(status().isConflict());
        assertNoMail();
    }

    @Test
    void anInvalidAddressIsRefusedInSwedish() throws Exception {
        adminInvites(olle, "inte en adress")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").value("Ange en giltig e-postadress."));
        mockMvc.perform(post("/admin/medlemmar/" + olle + "/inbjudan")
                        .param("email", "")
                        .with(asAdministrator())
                        .with(csrf()))
                .andExpect(flash().attribute("error", "Ange en e-postadress."));
        assertNoMail();
    }

    @Test
    void aHouseholdMemberInvitesThroughThePageAndTheApi() throws Exception {
        RequestPostProcessor erik = asMember(erikAccount, ERIK_EMAIL);

        assertRedirect(mockMvc.perform(post("/medlem/hushall/" + olle + "/inbjudan")
                        .param("email", OLLE_EMAIL)
                        .with(erik)
                        .with(csrf()))
                .andExpect(flash().attribute("notice", "En inbjudan är skickad till " + OLLE_EMAIL + "."))
                .andReturn(), "/medlem");
        assertThat(awaitMail().getTo()).containsExactly(OLLE_EMAIL);
        forgetMails();

        mockMvc.perform(post("/api/member/household/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\": " + olle + ", \"email\": \"" + OLLE_EMAIL + "\"}")
                        .with(erik)
                        .with(csrf()))
                .andExpect(status().isAccepted());
        assertThat(awaitMail().getTo()).containsExactly(OLLE_EMAIL);
    }

    /// Ingrid is in no household, so Erik cannot invite her, and a member cannot
    /// use the administrator's invitation.
    @Test
    void aMemberInvitesOnlyInTheirOwnHousehold() throws Exception {
        RequestPostProcessor erik = asMember(erikAccount, ERIK_EMAIL);

        mockMvc.perform(post("/medlem/hushall/" + ingrid + "/inbjudan")
                        .param("email", "ingrid@example.test")
                        .with(erik)
                        .with(csrf()))
                .andExpect(flash().attribute("error", "Personen finns inte i ditt hushåll."));
        mockMvc.perform(post("/api/member/household/invitations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\": " + ingrid + ", \"email\": \"ingrid@example.test\"}")
                        .with(erik)
                        .with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/admin/members/" + ingrid + "/invitation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"ingrid@example.test\"}")
                        .with(erik)
                        .with(csrf()))
                .andExpect(status().isForbidden());

        assertNoMail();
        assertThat(rowsIn("invitation")).isZero();
    }

    private ResultActions adminInvites(long member, String email) throws Exception {
        return mockMvc.perform(post("/api/admin/members/" + member + "/invitation")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"" + email + "\"}")
                .with(asAdministrator())
                .with(csrf()));
    }

    private ResultActions accept(String token) throws Exception {
        return mockMvc.perform(post("/api/invitations/acceptance")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\": \"" + token + "\"}")
                .with(csrf()));
    }

    private long accountOf(long member) {
        return jdbc.sql("SELECT COUNT(*) FROM account WHERE member_id = ?").param(member).query(Long.class).single();
    }
}
