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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/// Owner checks use valid requests with CSRF, so a forbidden result tests
/// ownership rather than authentication or request validation.
class HouseholdOwnershipIT extends MemberRegisterSupport {

    private static final String API = "/api/member/household";
    private static final String PAGE = "/medlem/hushall";

    private long household;
    private long owner;
    private long other;
    private RequestPostProcessor ownerLogin;
    private RequestPostProcessor otherLogin;

    @BeforeEach
    void household() {
        long account = insertAccount("Erik Exempel", "erik@example.test");
        owner = memberOf(account);
        ownerLogin = asMember(account, "erik@example.test");
        long otherAccount = insertAccount("Maria Exempel", "maria@example.test");
        other = memberOf(otherAccount);
        otherLogin = asMember(otherAccount, "maria@example.test");
        household = insertHousehold("Familjen Exempel");
        moveTo(owner, household);
        moveTo(other, household);
        jdbc.sql("UPDATE household SET owner_member_id = ? WHERE id = ?").params(owner, household).update();
    }

    @Test
    void nonOwnersCannotEditThroughEitherAdapterButCanLeave() throws Exception {
        mockMvc.perform(get(PAGE).with(otherLogin)).andExpect(status().isOk())
                .andExpect(content().string(containsString("Lämna hushållet")))
                .andExpect(content().string(not(containsString("Spara hushållets namn"))))
                .andExpect(content().string(not(containsString("Lägg till en person"))))
                .andExpect(content().string(not(containsString("Ändra uppgifter för Erik"))));
        mockMvc.perform(get(PAGE + "/ny").with(otherLogin)).andExpect(status().isForbidden());
        mockMvc.perform(put(API).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Ändrat\"}")
                .with(otherLogin).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(post(PAGE + "/namn").param("name", "Ändrat").with(otherLogin).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(API + "/members").contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Olle\"}").with(otherLogin).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(PAGE + "/medlemmar").param("fullName", "Olle").with(otherLogin).with(csrf()))
                .andExpect(status().isForbidden());
        for (long target : new long[] {owner, other}) {
            mockMvc.perform(put(API + "/members/" + target).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"fullName\":\"Ändrat\"}").with(otherLogin).with(csrf()))
                    .andExpect(status().isForbidden());
            mockMvc.perform(post(PAGE + "/medlemmar/" + target).param("fullName", "Ändrat")
                    .with(otherLogin).with(csrf())).andExpect(status().isForbidden());
        }
        mockMvc.perform(delete(API + "/members/" + owner).with(otherLogin).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(PAGE + "/medlemmar/" + owner + "/ta-bort").with(otherLogin))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(PAGE + "/medlemmar/" + owner + "/ta-bort").with(otherLogin).with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(jdbc.sql("SELECT name FROM household WHERE id = ?").param(household)
                .query(String.class).single()).isEqualTo("Familjen Exempel");
        assertThat(rowsIn("member")).isEqualTo(2);
        mockMvc.perform(delete(API + "/members/" + other).param("successorMemberId", String.valueOf(other))
                .with(otherLogin).with(csrf())).andExpect(status().isForbidden());
        mockMvc.perform(delete(API + "/members/" + other).with(otherLogin).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(owner()).isEqualTo(owner);
    }

    @Test
    void anOwnerMustChooseAnEligibleSuccessorBeforeLeaving() throws Exception {
        mockMvc.perform(delete(API + "/members/" + owner).with(ownerLogin).with(csrf()))
                .andExpect(status().isConflict());
        mockMvc.perform(post(PAGE + "/medlemmar/" + owner + "/ta-bort").with(ownerLogin).with(csrf()))
                .andExpect(content().string(containsString("Välj en annan person")));
        long withoutAccount = insertMember("Olle");
        moveTo(withoutAccount, household);
        long outsider = memberOf(insertAccount("Annan", "annan@example.test"));
        for (long invalid : new long[] {owner, withoutAccount, outsider, Long.MAX_VALUE}) {
            mockMvc.perform(delete(API + "/members/" + owner).param("successorMemberId", String.valueOf(invalid))
                    .with(ownerLogin).with(csrf())).andExpect(status().isNotFound());
        }
        assertThat(owner()).isEqualTo(owner);
        mockMvc.perform(delete(API + "/members/" + owner).param("successorMemberId", String.valueOf(other))
                .with(ownerLogin).with(csrf())).andExpect(status().isNoContent());
        assertThat(owner()).isEqualTo(other);
        mockMvc.perform(put(API).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Nytt\"}")
                .with(otherLogin).with(csrf())).andExpect(status().isOk());
        mockMvc.perform(put(API).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Fel\"}")
                .with(ownerLogin).with(csrf())).andExpect(status().isNotFound());
        assertThat(rowsIn("account")).isEqualTo(3);
    }

    @Test
    void administratorsTakeOverWhenNoAccountHolderRemains() throws Exception {
        moveTo(other, null);
        long child = insertMember("Olle");
        moveTo(child, household);
        mockMvc.perform(delete(API + "/members/" + owner).with(ownerLogin).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.sql("SELECT owner_member_id FROM household WHERE id = ?").param(household)
                .query().singleRow()).containsEntry("owner_member_id", null);
        assertThat(jdbc.sql("SELECT household_id FROM member WHERE id = ?").param(child)
                .query(Long.class).single()).isEqualTo(household);
        assertThat(rowsIn("account")).isEqualTo(2);
    }

    @Test
    void administratorsCanAssignAndChangeOwnershipThroughBothAdapters() throws Exception {
        String api = "/api/admin/households/" + household + "/owner";
        mockMvc.perform(put(api).contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\":%d}".formatted(other)).with(otherLogin).with(csrf()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(api).contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\":%d}".formatted(other)).with(asAdministrator()))
                .andExpect(status().isForbidden());
        mockMvc.perform(put(api).contentType(MediaType.APPLICATION_JSON)
                .content("{\"memberId\":%d}".formatted(other)).with(asAdministrator()).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.ownerMemberId").value(other));
        mockMvc.perform(put(API).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Fel\"}")
                .with(ownerLogin).with(csrf())).andExpect(status().isForbidden());
        assertRedirect(mockMvc.perform(post("/admin/hushall/" + household + "/agare")
                .param("memberId", String.valueOf(owner)).with(asAdministrator()).with(csrf())).andReturn(),
                "/admin/hushall");
        assertThat(owner()).isEqualTo(owner);
        mockMvc.perform(put(api).contentType(MediaType.APPLICATION_JSON).content("{\"memberId\":null}")
                .with(asAdministrator()).with(csrf())).andExpect(status().isOk());
        mockMvc.perform(put(API).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Fel\"}")
                .with(ownerLogin).with(csrf())).andExpect(status().isForbidden());
        long outsider = memberOf(insertAccount("Annan", "annan@example.test"));
        long child = insertMember("Olle");
        moveTo(child, household);
        for (long invalid : new long[] {outsider, child, Long.MAX_VALUE}) {
            mockMvc.perform(put(api).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"memberId\":%d}".formatted(invalid)).with(asAdministrator()).with(csrf()))
                    .andExpect(status().isNotFound());
        }
    }

    @Test
    void administratorMovesAndDeletionDoNotLeaveAnAbsentMemberAsOwner() throws Exception {
        mockMvc.perform(put("/api/admin/members/" + owner).contentType(MediaType.APPLICATION_JSON)
                .content("{\"fullName\":\"Erik\",\"email\":\"erik@example.test\",\"householdId\":null}")
                .with(asAdministrator()).with(csrf())).andExpect(status().isOk());
        assertThat(jdbc.sql("SELECT owner_member_id FROM household WHERE id = ?").param(household)
                .query().singleRow()).containsEntry("owner_member_id", null);
        jdbc.sql("UPDATE household SET owner_member_id = ? WHERE id = ?").params(other, household).update();
        mockMvc.perform(delete("/api/admin/members/" + other).with(asAdministrator()).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(jdbc.sql("SELECT owner_member_id FROM household WHERE id = ?").param(household)
                .query().singleRow()).containsEntry("owner_member_id", null);
        assertThat(rowsIn("household")).isEqualTo(1);
    }

    private long owner() {
        return jdbc.sql("SELECT owner_member_id FROM household WHERE id = ?").param(household)
                .query(Long.class).single();
    }
}
