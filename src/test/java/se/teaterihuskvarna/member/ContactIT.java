package se.teaterihuskvarna.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/// Proves that members see and change their own contact details (R012), through
/// the `/medlem` pages and `/api/member`: every field but the address they log
/// in with, and a Swedish message for what is wrong.
class ContactIT extends MemberRegisterSupport {

    private static final String ERIK = "erik@example.test";

    private long account;
    private RequestPostProcessor erik;

    @BeforeEach
    void erik() {
        account = insertAccount("Erik Lindqvist", ERIK);
        erik = asMember(account, ERIK);
    }

    @Test
    void thePageAndTheApiShowEveryField() throws Exception {
        jdbc.sql("""
                UPDATE member SET phone = '070-111', address = 'Exempelvägen 4', postal_code = '561 31',
                    city = 'Huskvarna' WHERE id = ?
                """).param(memberOf(account)).update();

        mockMvc.perform(get("/medlem").with(erik))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Erik Lindqvist")))
                .andExpect(content().string(containsString(ERIK)))
                .andExpect(content().string(containsString("070-111")))
                .andExpect(content().string(containsString("Exempelvägen 4")))
                .andExpect(content().string(containsString("561 31")))
                .andExpect(content().string(containsString("Huskvarna")));

        mockMvc.perform(get("/api/member").with(erik))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Erik Lindqvist"))
                .andExpect(jsonPath("$.email").value(ERIK))
                .andExpect(jsonPath("$.postalCode").value("561 31"))
                .andExpect(jsonPath("$.city").value("Huskvarna"));
    }

    /// An `email` field in the form is ignored: the address is the login, and
    /// only an administrator changes it.
    @Test
    void theFormChangesEverythingButTheAddress() throws Exception {
        MvcResult saved = mockMvc.perform(post("/medlem/kontaktuppgifter")
                        .param("fullName", "Erik L")
                        .param("phone", "070-222")
                        .param("address", "Provgatan 1")
                        .param("postalCode", "561 32")
                        .param("city", "Jönköping")
                        .param("email", "other@example.test")
                        .with(erik)
                        .with(csrf()))
                .andReturn();

        assertRedirect(saved, "/medlem");
        assertThat(contact()).containsEntry("full_name", "Erik L")
                .containsEntry("phone", "070-222")
                .containsEntry("address", "Provgatan 1")
                .containsEntry("postal_code", "561 32")
                .containsEntry("city", "Jönköping")
                .containsEntry("email", ERIK);
    }

    @Test
    void theApiChangesEverythingButTheAddress() throws Exception {
        mockMvc.perform(put("/api/member/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName": "Erik L", "phone": "070-222", "address": "Provgatan 1",
                                 "postalCode": "561 32", "city": "Jönköping", "email": "other@example.test"}
                                """)
                        .with(erik)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Erik L"))
                .andExpect(jsonPath("$.email").value(ERIK));

        assertThat(contact()).containsEntry("city", "Jönköping").containsEntry("email", ERIK);
    }

    @Test
    void aBlankNameIsRefusedInSwedish() throws Exception {
        mockMvc.perform(post("/medlem/kontaktuppgifter")
                        .param("fullName", " ")
                        .param("phone", "070-222")
                        .with(erik)
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ange ditt namn.")));

        mockMvc.perform(put("/api/member/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": \"\", \"phone\": \"" + "1".repeat(33) + "\"}")
                        .with(erik)
                        .with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fullName").value("Ange ditt namn."))
                .andExpect(jsonPath("$.errors.phone").value("Telefonnumret får vara högst 32 tecken."));

        assertThat(contact()).containsEntry("full_name", "Erik Lindqvist").containsEntry("phone", null);
    }

    @Test
    void nobodyLoggedOutReachesTheContactDetails() throws Exception {
        mockMvc.perform(get("/api/member")).andExpect(status().isUnauthorized());
        mockMvc.perform(put("/api/member/contact")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": \"X\"}")
                        .with(csrf()))
                .andExpect(status().isUnauthorized());
        assertRedirect(mockMvc.perform(get("/medlem/kontaktuppgifter")).andReturn(), "/logga-in");
    }

    private Map<String, Object> contact() {
        return jdbc.sql("""
                SELECT m.full_name, m.phone, m.address, m.postal_code, m.city, a.email
                FROM member m JOIN account a ON a.member_id = m.id WHERE a.id = ?
                """).param(account).query().singleRow();
    }
}
