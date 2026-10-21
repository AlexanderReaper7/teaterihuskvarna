package se.teaterihuskvarna.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import se.teaterihuskvarna.login.LoginKind;

/// Proves the administrator's member register (R018), households (R019) and the
/// CSV export (R021), through the `/admin/medlemmar` pages and
/// `/api/admin/members`: search, add, edit, delete with fees kept anonymised,
/// and that only an administrator gets in.
class MemberRegisterIT extends MemberRegisterSupport {

    private static final String ERIK = "erik@example.test";

    @Test
    void aMemberCannotReachTheRegister() throws Exception {
        long account = insertAccount("Erik Lindqvist", ERIK);
        RequestPostProcessor member = asMember(account, ERIK);

        mockMvc.perform(get("/api/admin/members").with(member)).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/members.csv").with(member)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/members/" + memberOf(account)).with(member).with(csrf()))
                .andExpect(status().isForbidden());
        // The pages send a member to the administrator login, as for anyone not an administrator.
        assertRedirect(mockMvc.perform(get("/admin/medlemmar").with(member)).andReturn(), "/admin/logga-in");
        assertRedirect(mockMvc.perform(get("/admin/medlemmar.csv").with(member)).andReturn(), "/admin/logga-in");
        mockMvc.perform(get("/api/admin/members")).andExpect(status().isUnauthorized());
        assertRedirect(mockMvc.perform(get("/admin/medlemmar")).andReturn(), "/admin/logga-in");

        assertThat(rowsIn("member")).isEqualTo(1);
    }

    @Test
    void theSearchMatchesNameEmailPhoneAndCityWhateverTheCase() throws Exception {
        long erik = memberOf(insertAccount("Erik Lindqvist", ERIK));
        jdbc.sql("UPDATE member SET phone = '070-111 22', city = 'Huskvarna' WHERE id = ?").param(erik).update();
        insertMember("Maria Bergström");

        for (String query : new String[] {"lindQVIST", "ERIK@EXAMPLE", "111 22", "huskVARNA"}) {
            mockMvc.perform(get("/api/admin/members").param("q", query).with(asAdministrator()))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.members.length()").value(1))
                    .andExpect(jsonPath("$.members[0].fullName").value("Erik Lindqvist"))
                    .andExpect(jsonPath("$.more").value(false));
        }
        mockMvc.perform(get("/api/admin/members").with(asAdministrator()))
                .andExpect(jsonPath("$.members.length()").value(2));
        mockMvc.perform(get("/admin/medlemmar").param("q", "berg").with(asAdministrator()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Maria Bergström")))
                .andExpect(content().string(not(containsString("Erik Lindqvist"))));
    }

    @Test
    void addingWithAnAddressCreatesAnAccountAndWithoutOneDoesNot() throws Exception {
        MvcResult added = mockMvc.perform(post("/admin/medlemmar")
                        .param("fullName", "Erik Lindqvist")
                        .param("email", ERIK)
                        .param("city", "Huskvarna")
                        .param("householdId", "")
                        .with(asAdministrator())
                        .with(csrf()))
                .andExpect(flash().attribute("notice", "Erik Lindqvist är tillagd."))
                .andReturn();
        long erik = jdbc.sql("SELECT id FROM member WHERE full_name = 'Erik Lindqvist'").query(Long.class).single();
        assertRedirect(added, "/admin/medlemmar/" + erik);
        assertThat(jdbc.sql("SELECT email FROM account WHERE member_id = ?").param(erik).query(String.class)
                .single()).isEqualTo(ERIK);

        mockMvc.perform(post("/api/admin/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": \"Olle Lindqvist\"}")
                        .with(asAdministrator())
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").doesNotExist());
        assertThat(rowsIn("member")).isEqualTo(2);
        assertThat(rowsIn("account")).isEqualTo(1);
    }

    @Test
    void aMemberWithoutANameIsRefusedInSwedish() throws Exception {
        mockMvc.perform(post("/admin/medlemmar")
                        .param("fullName", "")
                        .param("email", "inte en adress")
                        .with(asAdministrator())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ange ett namn.")))
                .andExpect(content().string(containsString("Ange en giltig e-postadress.")));

        mockMvc.perform(post("/api/admin/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": \" \"}")
                        .with(asAdministrator())
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fullName").value("Ange ett namn."));

        assertThat(rowsIn("member")).isZero();
    }

    /// Another account's address is taken whatever its case, on add and on edit.
    @Test
    void anAddressInUseIsAConflict() throws Exception {
        insertAccount("Erik Lindqvist", ERIK);
        long maria = memberOf(insertAccount("Maria Lindqvist", "maria@example.test"));

        mockMvc.perform(post("/api/admin/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": \"Erik igen\", \"email\": \"Erik@Example.test\"}")
                        .with(asAdministrator())
                        .with(csrf()))
                .andExpect(status().isConflict());
        mockMvc.perform(put("/api/admin/members/" + maria)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": \"Maria Lindqvist\", \"email\": \"ERIK@example.test\"}")
                        .with(asAdministrator())
                        .with(csrf()))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/admin/medlemmar/" + maria)
                        .param("fullName", "Maria Lindqvist")
                        .param("email", "erik@EXAMPLE.test")
                        .with(asAdministrator())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Adressen används redan av ett annat konto.")));

        assertThat(rowsIn("member")).isEqualTo(2);
        assertThat(jdbc.sql("SELECT email FROM account WHERE member_id = ?").param(maria).query(String.class)
                .single()).isEqualTo("maria@example.test");
    }

    @Test
    void anAdministratorEditsEveryFieldTheAddressAndHouseholdIncluded() throws Exception {
        long maria = memberOf(insertAccount("Maria Lindqvist", "maria@example.test"));
        long household = insertHousehold("Familjen Lindqvist");

        mockMvc.perform(put("/api/admin/members/" + maria)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Maria L", "email": "maria.l@example.test", "phone": "070-3",
                                 "address": "Provgatan 1", "postalCode": "561 32", "city": "Jönköping",
                                 "householdId": %d}
                                """.formatted(household))
                        .with(asAdministrator())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.household").value("Familjen Lindqvist"));

        Map<String, @Nullable Object> row = jdbc.sql("""
                SELECT m.full_name, m.phone, m.address, m.postal_code, m.city, m.household_id, a.email
                FROM member m JOIN account a ON a.member_id = m.id WHERE m.id = ?
                """).param(maria).query().singleRow();
        assertThat(row).containsEntry("full_name", "Maria L")
                .containsEntry("email", "maria.l@example.test")
                .containsEntry("phone", "070-3")
                .containsEntry("address", "Provgatan 1")
                .containsEntry("postal_code", "561 32")
                .containsEntry("city", "Jönköping")
                .containsEntry("household_id", household);
    }

    /// The member, account and sessions go; the fee rows stay with no member, so
    /// the year's income can still be counted.
    @Test
    void deletingRemovesTheMemberAndKeepsTheFeesAnonymised() throws Exception {
        long account = insertAccount("Erik Lindqvist", ERIK);
        long erik = memberOf(account);
        insertFee(erik, "INDIVIDUAL");
        MvcResult login = logInByLink(LoginKind.MEMBER, ERIK);
        assertThat(sessionsOf(account)).isEqualTo(1);

        mockMvc.perform(get("/admin/medlemmar/" + erik + "/ta-bort").with(asAdministrator()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ta bort Erik Lindqvist?")));
        assertThat(rowsIn("member")).isEqualTo(1);

        mockMvc.perform(delete("/api/admin/members/" + erik).with(asAdministrator()).with(csrf()))
                .andExpect(status().isNoContent());

        assertThat(rowsIn("member")).isZero();
        assertThat(rowsIn("account")).isZero();
        assertThat(sessionsOf(account)).isZero();
        assertThat(jdbc.sql("SELECT member_id, kind, amount_ore FROM fee").query().singleRow())
                .containsEntry("member_id", null)
                .containsEntry("kind", "INDIVIDUAL")
                .containsEntry("amount_ore", 5000);
        assertRedirect(mockMvc.perform(get("/medlem").with(sessionOf(login))).andReturn(), "/logga-in");
        mockMvc.perform(delete("/api/admin/members/" + erik).with(asAdministrator()).with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    void theDeleteFormDeletes() throws Exception {
        long olle = insertMember("Olle Lindqvist");
        insertFee(olle, "HOUSEHOLD");

        assertRedirect(mockMvc.perform(post("/admin/medlemmar/" + olle + "/ta-bort")
                        .with(asAdministrator())
                        .with(csrf()))
                .andExpect(flash().attribute("notice", "Medlemmen är borttagen."))
                .andReturn(), "/admin/medlemmar");

        assertThat(rowsIn("member")).isZero();
        assertThat(jdbc.sql("SELECT COUNT(*) FROM fee WHERE member_id IS NULL").query(Long.class).single())
                .isEqualTo(1);
    }

    @Test
    void householdsAreCreatedThroughBothAdapters() throws Exception {
        assertRedirect(mockMvc.perform(post("/admin/hushall")
                        .param("name", "Familjen Lindqvist")
                        .with(asAdministrator())
                        .with(csrf()))
                .andReturn(), "/admin/hushall");
        mockMvc.perform(post("/api/admin/households")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Familjen Bergström\"}")
                        .with(asAdministrator())
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Familjen Bergström"));
        mockMvc.perform(post("/api/admin/households")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\"}")
                        .with(asAdministrator())
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("Ange ett namn på hushållet."));

        mockMvc.perform(get("/api/admin/households").with(asAdministrator()))
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/admin/hushall").with(asAdministrator()))
                .andExpect(content().string(containsString("Familjen Bergström")));
    }

    @Test
    void aMemberSeesTheirHousehold() throws Exception {
        long account = insertAccount("Erik Lindqvist", ERIK);
        RequestPostProcessor erik = asMember(account, ERIK);
        mockMvc.perform(get("/api/member/household").with(erik)).andExpect(status().isNotFound());

        long household = insertHousehold("Familjen Lindqvist");
        moveTo(memberOf(account), household);
        moveTo(insertMember("Olle Lindqvist"), household);

        mockMvc.perform(get("/api/member/household").with(erik))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Familjen Lindqvist"))
                .andExpect(jsonPath("$.members.length()").value(2));
        mockMvc.perform(get("/medlem").with(erik))
                .andExpect(content().string(containsString("Olle Lindqvist")));
    }

    /// Both endpoints give the same file: a byte order mark for Excel, `;`
    /// between cells, and a cell that would start a formula made plain text.
    @Test
    void theExportIsACsvFileWithFormulasDefused() throws Exception {
        long erik = memberOf(insertAccount("=HYPERLINK(\"http://evil.test\")", ERIK));
        jdbc.sql("UPDATE member SET city = 'Huskvarna; Sverige' WHERE id = ?").param(erik).update();
        insertFee(erik, "INDIVIDUAL");

        for (String path : new String[] {"/admin/medlemmar.csv", "/api/admin/members.csv"}) {
            MvcResult result = mockMvc.perform(get(path).with(asAdministrator()))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Content-Type", "text/csv;charset=UTF-8"))
                    .andExpect(header().string("Content-Disposition", containsString("attachment")))
                    .andReturn();
            byte[] body = result.getResponse().getContentAsByteArray();
            assertThat(body).startsWith((byte) 0xEF, (byte) 0xBB, (byte) 0xBF);
            String text = new String(body, 3, body.length - 3, StandardCharsets.UTF_8);
            String[] lines = text.split("\r\n");
            assertThat(lines[0]).isEqualTo("Namn;E-post;Telefon;Adress;Postnummer;Ort;Hushåll;Betald "
                    + thisYear() + ";Medlem sedan");
            assertThat(lines[1]).startsWith("\"'=HYPERLINK(\"\"http://evil.test\"\")\";" + ERIK + ";;;;")
                    .contains("\"Huskvarna; Sverige\"");
        }
    }

    private long sessionsOf(long account) {
        return jdbc.sql("SELECT COUNT(*) FROM spring_session WHERE principal_name = ?")
                .param(LoginKind.MEMBER.principalName(account))
                .query(Long.class)
                .single();
    }
}
