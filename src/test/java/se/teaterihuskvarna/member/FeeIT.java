package se.teaterihuskvarna.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/// Proves the fee rules (R013, R019): what a member sees for this year, how an
/// administrator marks and undoes a payment through both adapters, and the
/// household rule, that a member is paid by a payment of their own or by a
/// household payment for the household they are in now.
///
/// Erik and Maria share a household, Johan is in none.
class FeeIT extends MemberRegisterSupport {

    private static final String ERIK_EMAIL = "erik@example.test";
    private static final String MARIA_EMAIL = "maria@example.test";
    private static final String JOHAN_EMAIL = "johan@example.test";

    @Autowired
    private FeeService fees;

    private long erikAccount;
    private long mariaAccount;
    private long johanAccount;
    private long erik;
    private long maria;
    private long johan;
    private long household;

    @BeforeEach
    void household() {
        household = insertHousehold("Familjen Lindqvist");
        erikAccount = insertAccount("Erik Lindqvist", ERIK_EMAIL);
        mariaAccount = insertAccount("Maria Lindqvist", MARIA_EMAIL);
        johanAccount = insertAccount("Johan Bergström", JOHAN_EMAIL);
        erik = memberOf(erikAccount);
        maria = memberOf(mariaAccount);
        johan = memberOf(johanAccount);
        moveTo(erik, household);
        moveTo(maria, household);
    }

    @Test
    void anUnpaidMemberSeesHowToPay() throws Exception {
        String year = String.valueOf(thisYear());
        mockMvc.perform(get("/medlem").with(asMember(johanAccount, JOHAN_EMAIL)))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Medlemsavgift " + year)))
                .andExpect(content().string(containsString("123-4567")))
                .andExpect(content().string(containsString("50 kr")))
                .andExpect(content().string(containsString("100 kr")))
                .andExpect(content().string(containsString("Skriv Johan Bergström i meddelandet.")));

        mockMvc.perform(get("/api/member").with(asMember(johanAccount, JOHAN_EMAIL)))
                .andExpect(jsonPath("$.fee.year").value(thisYear()))
                .andExpect(jsonPath("$.fee.paidAt").doesNotExist())
                .andExpect(jsonPath("$.fee.payment.bankgiro").value("123-4567"))
                .andExpect(jsonPath("$.fee.payment.individualOre").value(5000))
                .andExpect(jsonPath("$.fee.payment.householdOre").value(10_000))
                .andExpect(jsonPath("$.fee.payment.message").value("Johan Bergström"));
    }

    /// The amount comes from the settings when none is given, and the
    /// administrator who marked it is recorded.
    @Test
    void theWebFormMarksAPaymentWithTheConfiguredAmount() throws Exception {
        assertRedirect(mockMvc.perform(post("/admin/medlemmar/" + johan + "/avgift")
                        .param("kind", "INDIVIDUAL")
                        .param("kronor", "")
                        .with(asAdministrator())
                        .with(csrf()))
                .andExpect(flash().attribute("notice", "Avgiften är markerad som betald."))
                .andReturn(), "/admin/medlemmar/" + johan);

        assertThat(feeOf(johan)).containsEntry("kind", "INDIVIDUAL")
                .containsEntry("amount_ore", 5000)
                .containsEntry("year", thisYear())
                .containsEntry("marked_by", firstAdministratorId());
        mockMvc.perform(get("/medlem").with(asMember(johanAccount, JOHAN_EMAIL)))
                .andExpect(content().string(containsString("Betald ")))
                .andExpect(content().string(not(containsString("123-4567"))));
    }

    @Test
    void theApiMarksAPaymentWithAGivenAmountAndUndoesIt() throws Exception {
        mockMvc.perform(post("/api/admin/members/" + johan + "/fee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\": \"HOUSEHOLD\", \"amountOre\": 2500}")
                        .with(asAdministrator())
                        .with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(feeOf(johan)).containsEntry("kind", "HOUSEHOLD").containsEntry("amount_ore", 2500);

        mockMvc.perform(post("/api/admin/members/" + johan + "/fee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\": \"INDIVIDUAL\"}")
                        .with(asAdministrator())
                        .with(csrf()))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/api/admin/members/" + johan + "/fee").with(asAdministrator()).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(rowsIn("fee")).isZero();
        mockMvc.perform(delete("/api/admin/members/" + johan + "/fee").with(asAdministrator()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void aMarkWithoutAKindOrWithANegativeAmountIsRefusedInSwedish() throws Exception {
        mockMvc.perform(post("/api/admin/members/" + johan + "/fee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"amountOre\": -1}")
                        .with(asAdministrator())
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.kind")
                        .value("Välj om avgiften gäller en enskild medlem eller ett hushåll."))
                .andExpect(jsonPath("$.errors.amountOre").value("Ange beloppet i hela kronor, 0 eller mer."));

        mockMvc.perform(post("/admin/medlemmar/" + johan + "/avgift")
                        .param("kind", "INDIVIDUAL")
                        .param("kronor", "femtio")
                        .with(asAdministrator())
                        .with(csrf()))
                .andExpect(flash().attribute("error", "Ange beloppet i hela kronor, 0 eller mer."));

        assertThat(rowsIn("fee")).isZero();
    }

    /// A household payment by either of the two covers the other, and an
    /// individual payment covers only the one who paid.
    @Test
    void aHouseholdPaymentCoversTheHouseholdInBothDirections() throws Exception {
        insertFee(erik, "HOUSEHOLD");
        assertPaid(mariaAccount, MARIA_EMAIL, true);
        assertPaid(erikAccount, ERIK_EMAIL, false);

        jdbc.sql("DELETE FROM fee").update();
        insertFee(maria, "HOUSEHOLD");
        assertPaid(erikAccount, ERIK_EMAIL, true);
        assertPaid(mariaAccount, MARIA_EMAIL, false);
        assertUnpaid(johanAccount, JOHAN_EMAIL);

        jdbc.sql("DELETE FROM fee").update();
        insertFee(maria, "INDIVIDUAL");
        assertUnpaid(erikAccount, ERIK_EMAIL);
    }

    /// Coverage follows the household the member is in now.
    @Test
    void movingChangesWhichHouseholdPaymentCovers() throws Exception {
        insertFee(erik, "HOUSEHOLD");

        moveTo(maria, null);
        assertUnpaid(mariaAccount, MARIA_EMAIL);

        moveTo(johan, household);
        assertPaid(johanAccount, JOHAN_EMAIL, true);
    }

    /// The payment stays with the household it was paid for when the payer
    /// moves out, and the payer keeps it as their own.
    @Test
    void aHouseholdPaymentStaysWithTheHouseholdWhenThePayerMoves() throws Exception {
        insertFee(erik, "HOUSEHOLD");
        long other = insertHousehold("Familjen Bergström");
        moveTo(erik, other);
        moveTo(johan, other);

        assertPaid(mariaAccount, MARIA_EMAIL, true);
        assertPaid(erikAccount, ERIK_EMAIL, false);
        assertUnpaid(johanAccount, JOHAN_EMAIL);
    }

    /// Deleting the payer keeps the fee row without a member, and the
    /// household it paid for stays covered.
    @Test
    void aHouseholdPaymentOutlivesThePayer() throws Exception {
        insertFee(erik, "HOUSEHOLD");
        mockMvc.perform(delete("/api/admin/members/" + erik).with(asAdministrator()).with(csrf()))
                .andExpect(status().is2xxSuccessful());

        assertPaid(mariaAccount, MARIA_EMAIL, true);
    }

    @Test
    void theAdministratorPageShowsHouseholdCoverageAndHistory() throws Exception {
        insertFee(erik, "HOUSEHOLD");
        jdbc.sql("""
                INSERT INTO fee (member_id, year, kind, amount_ore, paid_at)
                VALUES (?, ?, 'INDIVIDUAL', 5000, now())
                """)
                .params(maria, thisYear() - 1)
                .update();

        mockMvc.perform(get("/admin/medlemmar/" + maria).with(asAdministrator()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Hushållets avgift täcker den.")))
                .andExpect(content().string(containsString(String.valueOf(thisYear() - 1))))
                .andExpect(content().string(containsString("Familjen Lindqvist")));

        mockMvc.perform(get("/api/admin/members/" + maria).with(asAdministrator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fees.length()").value(2))
                .andExpect(jsonPath("$.household.name").value("Familjen Lindqvist"));
    }

    @Test
    void theLatestPaidYearFollowsTheHouseholdRule() {
        insertFee(erik, "HOUSEHOLD");

        assertThat(fees.latestPaidYear(erik)).isEqualTo(thisYear());
        assertThat(fees.latestPaidYear(maria)).isEqualTo(thisYear());
        assertThat(fees.latestPaidYear(johan)).isNull();
    }

    @Test
    void aMemberCannotMarkAFee() throws Exception {
        RequestPostProcessor member = asMember(johanAccount, JOHAN_EMAIL);
        mockMvc.perform(post("/api/admin/members/" + johan + "/fee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\": \"INDIVIDUAL\"}")
                        .with(member)
                        .with(csrf()))
                .andExpect(status().isForbidden());
        assertRedirect(mockMvc.perform(post("/admin/medlemmar/" + johan + "/avgift")
                        .param("kind", "INDIVIDUAL")
                        .with(member)
                        .with(csrf()))
                .andReturn(), "/admin/logga-in");
        assertThat(rowsIn("fee")).isZero();
    }

    private Map<String, Object> feeOf(long member) {
        return jdbc.sql("SELECT kind, amount_ore, year, marked_by FROM fee WHERE member_id = ?")
                .param(member)
                .query()
                .singleRow();
    }

    private void assertPaid(long account, String email, boolean throughHousehold) throws Exception {
        mockMvc.perform(get("/api/member").with(asMember(account, email)))
                .andExpect(jsonPath("$.fee.paidAt").isNotEmpty())
                .andExpect(jsonPath("$.fee.throughHousehold").value(throughHousehold));
    }

    private void assertUnpaid(long account, String email) throws Exception {
        mockMvc.perform(get("/api/member").with(asMember(account, email)))
                .andExpect(jsonPath("$.fee.paidAt").doesNotExist())
                .andExpect(jsonPath("$.fee.payment.bankgiro").value("123-4567"));
    }
}
