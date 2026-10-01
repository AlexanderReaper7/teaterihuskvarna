package se.teaterihuskvarna.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import se.teaterihuskvarna.login.LoginKind;

/// Member household management through both adapters, with scope checks,
/// account preservation, fee changes, leaving and concurrent creation.
class MemberHouseholdIT extends MemberRegisterSupport {

    private static final String ERIK = "erik@example.test";
    private static final String MARIA = "maria@example.test";
    private static final String API = "/api/member/household";
    private static final String PAGE = "/medlem/hushall";

    @Autowired
    private HouseholdService households;

    private long account;
    private long ownMember;
    private RequestPostProcessor erik;

    @BeforeEach
    void member() {
        account = insertAccount("Erik Lindqvist", ERIK);
        ownMember = memberOf(account);
        erik = asMember(account, ERIK);
    }

    @Test
    void creationAttachesTheCallerAndRenamingKeepsTheHousehold() throws Exception {
        mockMvc.perform(get("/medlem").with(erik))
                .andExpect(content().string(containsString("Skapa ett hushåll")));
        mockMvc.perform(get(PAGE).with(erik)).andExpect(status().isOk());
        assertRedirect(mockMvc.perform(post(PAGE).param("name", " Familjen Lindqvist ")
                .with(erik).with(csrf())).andReturn(), PAGE);
        long household = householdOf(ownMember);
        mockMvc.perform(get(API).with(erik))
                .andExpect(jsonPath("$.name").value("Familjen Lindqvist"))
                .andExpect(jsonPath("$.members[0].id").value(ownMember));

        mockMvc.perform(put(API).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" Familjen L \"}").with(erik).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(household))
                .andExpect(jsonPath("$.name").value("Familjen L"));
        assertRedirect(mockMvc.perform(post(PAGE + "/namn").param("name", "Familjen igen")
                .with(erik).with(csrf())).andReturn(), PAGE);
        assertThat(householdOf(ownMember)).isEqualTo(household);
        assertThat(rowsIn("household")).isEqualTo(1);
    }

    @Test
    void creationThroughTheApiRefusesToReplaceAnExistingHousehold() throws Exception {
        mockMvc.perform(post(API).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Familjen\"}").with(erik).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.members.length()").value(1));
        long household = householdOf(ownMember);
        mockMvc.perform(post(API).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Ett annat\"}").with(erik).with(csrf()))
                .andExpect(status().isConflict());
        assertRedirect(mockMvc.perform(post(PAGE).param("name", "Ett annat")
                .with(erik).with(csrf())).andReturn(), PAGE);
        assertThat(rowsIn("household")).isEqualTo(1);
        assertThat(householdOf(ownMember)).isEqualTo(household);
    }

    @Test
    void concurrentCreationMakesExactlyOneHousehold() throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> running = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 2; i++) {
                running.add(pool.submit(() -> {
                    start.await();
                    try {
                        households.createForAccount(account, new NewHousehold("Familjen"));
                        return true;
                    } catch (AlreadyInHousehold e) {
                        return false;
                    }
                }));
            }
            start.countDown();
        }
        List<Boolean> outcomes = new ArrayList<>();
        for (Future<Boolean> result : running) {
            outcomes.add(result.get(10, TimeUnit.SECONDS));
        }
        assertThat(outcomes).containsExactlyInAnyOrder(true, false);
        assertThat(rowsIn("household")).isEqualTo(1);
        assertThat(householdOf(ownMember)).isPositive();
    }

    @Test
    void invalidFormsKeepValuesAndDoNotWrite() throws Exception {
        mockMvc.perform(post(PAGE).param("name", " ").with(erik).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Ange ett namn på hushållet.")));
        mockMvc.perform(post(API).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}").with(erik).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists());
        assertThat(rowsIn("household")).isZero();
        moveTo(ownMember, insertHousehold("Familjen"));
        mockMvc.perform(post(PAGE + "/namn").param("name", " ").with(erik).with(csrf()))
                .andExpect(content().string(containsString("Ange ett namn på hushållet.")));
        mockMvc.perform(post(PAGE + "/medlemmar").param("fullName", "").param("city", "Huskvarna")
                        .with(erik).with(csrf()))
                .andExpect(content().string(containsString("Ange ditt namn.")))
                .andExpect(content().string(containsString("value=\"Huskvarna\"")));
        mockMvc.perform(post(API + "/members").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"\"}").with(erik).with(csrf()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.fullName").exists());
        assertThat(rowsIn("member")).isEqualTo(1);
    }

    @Test
    void newPeopleHaveNoAccountAndCanBeEditedAndInvited() throws Exception {
        long household = insertHousehold("Familjen");
        moveTo(ownMember, household);
        mockMvc.perform(get(PAGE + "/ny").with(erik)).andExpect(status().isOk());
        assertRedirect(mockMvc.perform(post(PAGE + "/medlemmar").param("fullName", "Olle Lindqvist")
                .param("address", "Provgatan 1").with(erik).with(csrf())).andReturn(), PAGE);
        long olle = jdbc.sql("SELECT id FROM member WHERE full_name = 'Olle Lindqvist'")
                .query(Long.class).single();
        assertThat(householdOf(olle)).isEqualTo(household);
        assertThat(rowsIn("account")).isEqualTo(1);
        mockMvc.perform(get(API + "/members/" + olle).with(erik))
                .andExpect(jsonPath("$.address").value("Provgatan 1"));
        assertRedirect(mockMvc.perform(post(PAGE + "/medlemmar/" + olle).param("fullName", "Olle L")
                .param("phone", "070-123").with(erik).with(csrf())).andReturn(), PAGE);
        mockMvc.perform(post(API + "/members").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Anna Lindqvist\"}").with(erik).with(csrf()))
                .andExpect(status().isCreated());
        mockMvc.perform(post(API + "/invitations").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberId\":%d,\"email\":\"olle@example.test\"}".formatted(olle))
                        .with(erik).with(csrf()))
                .andExpect(status().isAccepted());
        assertThat(rowsIn("invitation")).isEqualTo(1);
        String token = tokenIn(awaitMail(), "/inbjudan");
        mockMvc.perform(delete(API + "/members/" + olle).with(erik).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(rowsIn("member")).isEqualTo(3);
        assertThat(rowsIn("invitation")).isEqualTo(1);
        assertThat(jdbc.sql("SELECT household_id FROM member WHERE id = ?").param(olle)
                .query().singleRow()).containsEntry("household_id", null);
        mockMvc.perform(post("/api/invitations/acceptance").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"%s\"}".formatted(token)).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(rowsIn("account")).isEqualTo(2);
    }

    @Test
    void editingAnAccountHolderPreservesTheirEmailAndLogin() throws Exception {
        long household = insertHousehold("Familjen");
        moveTo(ownMember, household);
        long mariaAccount = insertAccount("Maria Lindqvist", MARIA);
        long maria = memberOf(mariaAccount);
        moveTo(maria, household);
        MvcResult login = logInByLink(LoginKind.MEMBER, MARIA);

        mockMvc.perform(get(PAGE + "/medlemmar/" + maria).with(erik))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Maria Lindqvist")));
        mockMvc.perform(put(API + "/members/" + maria).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":" Maria L ","phone":"070-4","address":"Provgatan 2",
                                 "postalCode":"561 32","city":"Huskvarna"}
                                """).with(erik).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Maria L"));
        assertThat(jdbc.sql("SELECT email FROM account WHERE id = ?").param(mariaAccount)
                .query(String.class).single()).isEqualTo(MARIA);
        mockMvc.perform(get("/api/member").with(sessionOf(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Maria L"))
                .andExpect(jsonPath("$.phone").value("070-4"))
                .andExpect(jsonPath("$.householdId").value(household));
    }

    @Test
    void removalKeepsMembershipLoginAndPaymentsAndChangesCoverage() throws Exception {
        long household = insertHousehold("Familjen");
        moveTo(ownMember, household);
        insertFee(ownMember, "HOUSEHOLD");
        long mariaAccount = insertAccount("Maria Lindqvist", MARIA);
        long maria = memberOf(mariaAccount);
        moveTo(maria, household);
        MvcResult login = logInByLink(LoginKind.MEMBER, MARIA);
        mockMvc.perform(get("/api/member").with(sessionOf(login)))
                .andExpect(jsonPath("$.fee.throughHousehold").value(true));
        mockMvc.perform(get(PAGE + "/medlemmar/" + maria + "/ta-bort").with(erik))
                .andExpect(status().isOk());
        assertRedirect(mockMvc.perform(post(PAGE + "/medlemmar/" + maria + "/ta-bort")
                .with(erik).with(csrf())).andReturn(), "/medlem");
        mockMvc.perform(get("/api/member").with(sessionOf(login)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.householdId").doesNotExist())
                .andExpect(jsonPath("$.fee.paidAt").doesNotExist());
        assertThat(rowsIn("member")).isEqualTo(2);
        assertThat(rowsIn("account")).isEqualTo(2);
        assertThat(rowsIn("fee")).isEqualTo(1);

        insertFee(maria, "INDIVIDUAL");
        moveTo(maria, household);
        mockMvc.perform(delete(API + "/members/" + maria).with(erik).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/member").with(sessionOf(login)))
                .andExpect(jsonPath("$.fee.paidAt").exists())
                .andExpect(jsonPath("$.fee.throughHousehold").value(false));
        assertThat(rowsIn("fee")).isEqualTo(2);
    }

    @Test
    void leavingAnExistingHouseholdAllowsCreationAndRemovesOldPermissions() throws Exception {
        long oldHousehold = insertHousehold("Familjen");
        long maria = memberOf(insertAccount("Maria Lindqvist", MARIA));
        moveTo(ownMember, oldHousehold);
        moveTo(maria, oldHousehold);
        mockMvc.perform(get(PAGE).with(erik))
                .andExpect(content().string(containsString("Lämna hushållet")));
        assertRedirect(mockMvc.perform(post(PAGE + "/medlemmar/" + ownMember + "/ta-bort")
                .with(erik).with(csrf())).andReturn(), "/medlem");
        mockMvc.perform(get(API).with(erik)).andExpect(status().isNotFound());
        mockMvc.perform(post(API).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Eget hushåll\"}").with(erik).with(csrf()))
                .andExpect(status().isCreated());
        assertThat(householdOf(ownMember)).isNotEqualTo(oldHousehold);
        assertThat(householdOf(maria)).isEqualTo(oldHousehold);
        mockMvc.perform(delete(API + "/members/" + maria).with(erik).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(API + "/members/" + ownMember).with(erik).with(csrf()))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/member").with(erik)).andExpect(status().isOk());
    }

    @Test
    void noHouseholdGrantsNoManagementPermissions() throws Exception {
        mockMvc.perform(get(PAGE + "/ny").with(erik)).andExpect(status().isNotFound());
        mockMvc.perform(put(API).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Familjen\"}").with(erik).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(post(API + "/members").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Olle\"}").with(erik).with(csrf()))
                .andExpect(status().isNotFound());
        assertThat(rowsIn("member")).isEqualTo(1);
    }

    @Test
    void anotherHouseholdsMembersAreInaccessibleThroughBothAdapters() throws Exception {
        moveTo(ownMember, insertHousehold("Familjen"));
        long stranger = insertMember("Annan medlem");
        long otherHousehold = insertHousehold("Annat hushåll");
        moveTo(stranger, otherHousehold);
        for (long target : new long[] {stranger, Long.MAX_VALUE}) {
            String api = API + "/members/" + target;
            String page = PAGE + "/medlemmar/" + target;
            mockMvc.perform(get(api).with(erik)).andExpect(status().isNotFound());
            mockMvc.perform(put(api).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"fullName\":\"Ändrat\"}").with(erik).with(csrf()))
                    .andExpect(status().isNotFound());
            mockMvc.perform(delete(api).with(erik).with(csrf())).andExpect(status().isNotFound());
            mockMvc.perform(get(page).with(erik)).andExpect(status().isNotFound());
            mockMvc.perform(post(page).param("fullName", "Ändrat").with(erik).with(csrf()))
                    .andExpect(status().isNotFound());
            mockMvc.perform(get(page + "/ta-bort").with(erik)).andExpect(status().isNotFound());
            mockMvc.perform(post(page + "/ta-bort").with(erik).with(csrf()))
                    .andExpect(status().isNotFound());
        }
        assertThat(householdOf(stranger)).isEqualTo(otherHousehold);
        assertThat(jdbc.sql("SELECT full_name FROM member WHERE id = ?").param(stranger)
                .query(String.class).single()).isEqualTo("Annan medlem");
    }

    @Test
    void householdChangesRequireAMemberLoginAndCsrf() throws Exception {
        mockMvc.perform(get(API)).andExpect(status().isUnauthorized());
        mockMvc.perform(get(API).with(asAdministrator())).andExpect(status().isForbidden());
        assertRedirect(mockMvc.perform(get(PAGE)).andReturn(), "/logga-in");
        mockMvc.perform(post(API).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Familjen\"}")
                        .with(csrf())).andExpect(status().isUnauthorized());
        mockMvc.perform(post(API).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Familjen\"}")
                        .with(asAdministrator()).with(csrf())).andExpect(status().isForbidden());
        assertRedirect(mockMvc.perform(post(PAGE).param("name", "Familjen").with(erik)).andReturn(),
                "/logga-in?gammal");
        mockMvc.perform(post(API).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Familjen\"}")
                        .with(erik)).andExpect(status().isForbidden());
        mockMvc.perform(put(API).contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"Familjen\"}")
                        .with(erik)).andExpect(status().isForbidden());
        mockMvc.perform(post(API + "/members").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Olle\"}").with(erik)).andExpect(status().isForbidden());
        mockMvc.perform(put(API + "/members/" + ownMember).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"Ändrat\"}").with(erik)).andExpect(status().isForbidden());
        mockMvc.perform(delete(API + "/members/" + ownMember).with(erik)).andExpect(status().isForbidden());
        assertThat(rowsIn("household")).isZero();
    }

    private long householdOf(long memberId) {
        return jdbc.sql("SELECT household_id FROM member WHERE id = ?").param(memberId).query(Long.class).single();
    }
}
