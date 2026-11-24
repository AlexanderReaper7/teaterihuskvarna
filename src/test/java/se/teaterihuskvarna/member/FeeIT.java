package se.teaterihuskvarna.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import se.teaterihuskvarna.login.AdministratorId;

/// Proves the fee rules (R013, R019): what a member sees for this year, how an
/// administrator marks and undoes a payment through both adapters, and the
/// household rule, that a member is paid by a payment of their own or by a
/// household payment for the household they are in now. A household payment
/// made before the payer has a household is for the one the payer gains.
///
/// The fee year is the year in Sweden by the application's clock. The tests
/// of the turn of the year stop that clock on New Year's night, in a ledger
/// made by hand, as `ShiftIT` does for its service.
///
/// Erik and Maria share a household, Johan is in none.
class FeeIT extends MemberRegisterSupport {

    private static final String ERIK_EMAIL = "erik@example.test";
    private static final String MARIA_EMAIL = "maria@example.test";
    private static final String JOHAN_EMAIL = "johan@example.test";

    /// 23:30 on 31 December in Sweden, which is 22:30 UTC.
    private static final Instant BEFORE_MIDNIGHT_IN_SWEDEN = Instant.parse("2026-12-31T22:30:00Z");

    /// 00:30 on 1 January in Sweden, while the UTC date is still 31 December.
    private static final Instant AFTER_MIDNIGHT_IN_SWEDEN = Instant.parse("2026-12-31T23:30:00Z");

    @Autowired
    private FeeService fees;

    @Autowired
    private FeeRepository feeRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AssociationSettings association;


    @Autowired
    private PlatformTransactionManager transactions;

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
                .andExpect(content().string(containsString(
                        "Skriv exakt " + JOHAN_EMAIL + " i meddelandet och inget annat.")));

        mockMvc.perform(get("/api/member").with(asMember(johanAccount, JOHAN_EMAIL)))
                .andExpect(jsonPath("$.fee.year").value(thisYear()))
                .andExpect(jsonPath("$.fee.paidAt").doesNotExist())
                .andExpect(jsonPath("$.fee.payment.bankgiro").value("123-4567"))
                .andExpect(jsonPath("$.fee.payment.individualOre").value(5000))
                .andExpect(jsonPath("$.fee.payment.householdOre").value(10_000))
                .andExpect(jsonPath("$.fee.payment.message").value(JOHAN_EMAIL));
    }

    /// The register reads the listed members' accounts in one query, and each
    /// payment instruction still names the member's own address (#91). A member
    /// without an account gets no instruction. An address looked up by the
    /// wrong id shows, because no account id is also a member id
    /// ([IntegrationTestSupport#idsApart]).
    @Test
    void eachMemberInTheRegisterIsToldToWriteTheirOwnAddress() throws Exception {
        insertMember("Olle Utan Konto");
        insertAccount("Nils Nyberg", "nils@example.test");
        mockMvc.perform(get("/api/admin/members").with(asAdministrator()))
                .andExpect(jsonPath("$.members.length()").value(5))
                .andExpect(jsonPath("$.members[0].fullName").value("Erik Lindqvist"))
                .andExpect(jsonPath("$.members[0].fee.payment.message").value(ERIK_EMAIL))
                .andExpect(jsonPath("$.members[1].fullName").value("Johan Bergström"))
                .andExpect(jsonPath("$.members[1].fee.payment.message").value(JOHAN_EMAIL))
                .andExpect(jsonPath("$.members[2].fullName").value("Maria Lindqvist"))
                .andExpect(jsonPath("$.members[2].fee.payment.message").value(MARIA_EMAIL))
                .andExpect(jsonPath("$.members[3].fullName").value("Nils Nyberg"))
                .andExpect(jsonPath("$.members[3].fee.payment.message").value("nils@example.test"))
                .andExpect(jsonPath("$.members[4].fullName").value("Olle Utan Konto"))
                .andExpect(jsonPath("$.members[4].fee.payment").doesNotExist());
    }

    /// The badge by the heading on /medlem (#78) says the status in words, and
    /// follows the household rule. The span is matched whole because "Betald"
    /// is also the start of the paid date line.
    @Test
    void theMemberPageShowsTheFeeStatusAsABadge() throws Exception {
        String unpaid = "<span class=\"badge badge-unpaid\">Ej betald</span>";
        String paid = "<span class=\"badge badge-paid\">Betald</span>";
        mockMvc.perform(get("/medlem").with(asMember(johanAccount, JOHAN_EMAIL)))
                .andExpect(content().string(containsString(unpaid)))
                .andExpect(content().string(not(containsString(paid))))
                .andExpect(content().string(containsString("123-4567")));

        insertFee(erik, "HOUSEHOLD");
        mockMvc.perform(get("/medlem").with(asMember(erikAccount, ERIK_EMAIL)))
                .andExpect(content().string(containsString(paid)))
                .andExpect(content().string(not(containsString(unpaid))));
        mockMvc.perform(get("/medlem").with(asMember(mariaAccount, MARIA_EMAIL)))
                .andExpect(content().string(containsString(paid)))
                .andExpect(content().string(not(containsString(unpaid))));
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
    void theRegisterListsWhoHasAndHasNotPaidThisYear() throws Exception {
        insertFee(erik, "HOUSEHOLD");

        String unpaid = page("/admin/medlemmar?avgift=obetald");
        assertThat(unpaid).contains("Johan Bergström").doesNotContain("Erik Lindqvist", "Maria Lindqvist")
                .contains("aria-current=\"page\">Har inte betalat " + thisYear() + "<");
        assertThat(page("/admin/medlemmar?avgift=betald"))
                .contains("Erik Lindqvist", "Maria Lindqvist").doesNotContain("Johan Bergström");
        assertThat(page("/admin/medlemmar?avgift=obetald&q=lindqvist")).doesNotContain("Lindqvist</a>");
        assertThat(page("/admin/medlemmar?avgift=okänt")).contains("Johan Bergström", "Erik Lindqvist");
        // The fee column and the member's page say the status with the same badge as /medlem (#110).
        assertThat(unpaid).contains("<span class=\"badge badge-unpaid\">Ej betald</span>");
        assertThat(page("/admin/medlemmar?avgift=betald")).contains("<span class=\"badge badge-paid\">Betald</span>",
                "<th scope=\"col\">Markerad som betald</th>");
        assertThat(page("/admin/medlemmar/" + johan)).contains("<span class=\"badge badge-unpaid\">Ej betald</span>");

        mockMvc.perform(get("/api/admin/members").param("fee", "UNPAID").with(asAdministrator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.members[*].fullName", containsInAnyOrder("Johan Bergström")));
        mockMvc.perform(get("/api/admin/members").param("fee", "PAID").with(asAdministrator()))
                .andExpect(jsonPath("$.members[*].fullName",
                        containsInAnyOrder("Erik Lindqvist", "Maria Lindqvist")));
    }

    @Test
    void theStartPageCountsWhoHasPaidAndLinksToTheRest() throws Exception {
        insertFee(erik, "HOUSEHOLD");

        // The link's icon is left out, so the text is compared on its own.
        assertThat(page("/admin").replaceAll("(?s)<svg.*?</svg>", "").replaceAll("\\s+", " "))
                .contains("Medlemsavgifter " + thisYear(), "2 av 3 medlemmar har betalat.")
                .contains("<a href=\"/admin/medlemmar?avgift=obetald\"> Visa den som inte har betalat</a>");
        mockMvc.perform(get("/api/admin/fees").with(asAdministrator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(thisYear()))
                .andExpect(jsonPath("$.paid").value(2))
                .andExpect(jsonPath("$.unpaid").value(1));

        insertFee(johan, "INDIVIDUAL");
        assertThat(page("/admin")).contains("3 av 3 medlemmar har betalat.").doesNotContain("inte har betalat");
    }

    @Test
    void aButtonInTheRegisterMarksAFeeAndGoesBackToTheSameList() throws Exception {
        String register = page("/admin/medlemmar?avgift=obetald");
        assertThat(register)
                .contains("Betald, enskild: Johan Bergström")
                .doesNotContain("Betald, hushåll: Johan Bergström")
                .contains("Betald, hushåll: Erik Lindqvist och alla i hushållet");

        String back = "/admin/medlemmar?q=Bergstr%C3%B6m&avgift=obetald";
        MvcResult first = markFromRegister(johan);
        assertRedirect(first, back);
        assertThat(first.getFlashMap().get("notice")).isEqualTo("Avgiften för Johan Bergström är markerad som betald.");
        MvcResult again = markFromRegister(johan);
        assertRedirect(again, back);
        assertThat(again.getFlashMap().get("error")).isEqualTo("Medlemmen har redan en betalning i år.");
        assertThat(feeOf(johan)).containsEntry("kind", "INDIVIDUAL").containsEntry("amount_ore", 5000);
        assertThat(page("/admin/medlemmar?avgift=obetald")).doesNotContain("Johan Bergström");
    }

    @Test
    void aSearchKeepsAPlusAndBracesInTheFilterLinksAndAfterMarkingAFee() throws Exception {
        assertSearchSurvives(johan, "a+b", "a%2Bb");
        assertSearchSurvives(maria, "{a}", "%7Ba%7D");
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

    /// Pay first, create the household afterwards, add the family: the fee
    /// was marked while the payer had no household, and covers the household
    /// from the moment the payer creates it.
    @Test
    void aHouseholdFeePaidBeforeTheHouseholdExistsCoversTheHouseholdThePayerCreates() throws Exception {
        markHouseholdFee(johan);
        RequestPostProcessor asJohan = asMember(johanAccount, JOHAN_EMAIL);

        mockMvc.perform(post("/api/member/household")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"Familjen Bergström\"}")
                        .with(asJohan)
                        .with(csrf()))
                .andExpect(status().isCreated());
        long lisa = Long.parseLong(mockMvc.perform(post("/api/member/household/members")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": \"Lisa Bergström\"}")
                        .with(asJohan)
                        .with(csrf()))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));

        assertPaid(johanAccount, JOHAN_EMAIL, false);
        assertCoveredByHousehold(lisa);
        mockMvc.perform(get("/api/admin/members").param("fee", "PAID").with(asAdministrator()))
                .andExpect(jsonPath("$.members[*].fullName",
                        containsInAnyOrder("Johan Bergström", "Lisa Bergström")));
        assertThat(fees.latestPaidYear(new MemberId(johan))).isEqualTo(thisYear());
        assertThat(fees.latestPaidYear(new MemberId(lisa))).isEqualTo(thisYear());
        assertThat(feeHouseholdOf(johan)).isEqualTo(householdOf(johan));
    }

    /// The same payment, and an administrator places the payer in a household
    /// that already has members: it covers them from then on.
    @Test
    void aHouseholdFeePaidBeforeAnAdministratorPlacesThePayerCoversThatHousehold() throws Exception {
        markHouseholdFee(johan);
        assertUnpaid(erikAccount, ERIK_EMAIL);

        placeInHousehold(johan, "Johan Bergström", JOHAN_EMAIL, household);

        assertPaid(johanAccount, JOHAN_EMAIL, false);
        assertPaid(erikAccount, ERIK_EMAIL, true);
        assertPaid(mariaAccount, MARIA_EMAIL, true);
        assertThat(fees.latestPaidYear(new MemberId(johan))).isEqualTo(thisYear());
        assertThat(fees.latestPaidYear(new MemberId(erik))).isEqualTo(thisYear());
        assertThat(fees.latestPaidYear(new MemberId(maria))).isEqualTo(thisYear());
        assertThat(feeHouseholdOf(johan)).isEqualTo(household);
    }

    /// Only a household fee follows the payer into a household. An individual
    /// fee paid before the household exists still covers the payer alone.
    @Test
    void anIndividualFeePaidBeforeTheHouseholdExistsCoversOnlyThePayer() throws Exception {
        insertFee(johan, "INDIVIDUAL");

        placeInHousehold(johan, "Johan Bergström", JOHAN_EMAIL, household);

        assertThat(feeHouseholdOf(johan)).isNull();
        assertPaid(johanAccount, JOHAN_EMAIL, false);
        assertUnpaid(erikAccount, ERIK_EMAIL);
    }

    /// A payment that already names a household stays with it when an
    /// administrator moves the payer, as it does when the payer is moved in
    /// the database above.
    @Test
    void aHouseholdPaymentStaysWithItsHouseholdWhenAnAdministratorMovesThePayer() throws Exception {
        insertFee(erik, "HOUSEHOLD");
        long other = insertHousehold("Familjen Bergström");
        moveTo(johan, other);

        placeInHousehold(erik, "Erik Lindqvist", ERIK_EMAIL, other);

        assertThat(feeHouseholdOf(erik)).isEqualTo(household);
        assertPaid(mariaAccount, MARIA_EMAIL, true);
        assertPaid(erikAccount, ERIK_EMAIL, false);
        assertUnpaid(johanAccount, JOHAN_EMAIL);
    }

    /// A payer who joins a household that is already covered brings a second
    /// household payment to it. No rule says otherwise, so either payment
    /// keeps the household covered when the other is undone.
    @Test
    void aSecondHouseholdPaymentKeepsTheHouseholdCoveredWhenTheFirstIsUndone() throws Exception {
        insertFee(erik, "HOUSEHOLD");
        markHouseholdFee(johan);
        placeInHousehold(johan, "Johan Bergström", JOHAN_EMAIL, household);

        mockMvc.perform(delete("/api/admin/members/" + erik + "/fee").with(asAdministrator()).with(csrf()))
                .andExpect(status().isNoContent());

        assertPaid(johanAccount, JOHAN_EMAIL, false);
        assertPaid(erikAccount, ERIK_EMAIL, true);
        assertPaid(mariaAccount, MARIA_EMAIL, true);
    }

    /// Payment does not carry forward. A member whose only payment is last
    /// year's, their own or their household's, is unpaid this year everywhere
    /// the status shows, and their Brevo contact keeps last year.
    @Test
    void lastYearsPaymentDoesNotCountForThisYear() throws Exception {
        insertFee(johan, "INDIVIDUAL", thisYear() - 1);
        insertFee(erik, "HOUSEHOLD", thisYear() - 1);

        assertUnpaid(johanAccount, JOHAN_EMAIL);
        assertUnpaid(erikAccount, ERIK_EMAIL);
        assertUnpaid(mariaAccount, MARIA_EMAIL);
        mockMvc.perform(get("/api/admin/members").param("fee", "UNPAID").with(asAdministrator()))
                .andExpect(jsonPath("$.members[*].fullName",
                        containsInAnyOrder("Erik Lindqvist", "Johan Bergström", "Maria Lindqvist")));
        mockMvc.perform(get("/api/admin/fees").with(asAdministrator()))
                .andExpect(jsonPath("$.year").value(thisYear()))
                .andExpect(jsonPath("$.paid").value(0))
                .andExpect(jsonPath("$.unpaid").value(3));
        mockMvc.perform(get("/api/admin/members/" + johan).with(asAdministrator()))
                .andExpect(jsonPath("$.fees.length()").value(2))
                .andExpect(jsonPath("$.fees[0].year").value(thisYear()))
                .andExpect(jsonPath("$.fees[0].paidAt").doesNotExist())
                .andExpect(jsonPath("$.fees[1].year").value(thisYear() - 1))
                .andExpect(jsonPath("$.fees[1].paidAt").isNotEmpty());
        assertThat(fees.latestPaidYear(new MemberId(johan))).isEqualTo(thisYear() - 1);
        assertThat(fees.latestPaidYear(new MemberId(maria))).isEqualTo(thisYear() - 1);
    }

    /// Midnight in Sweden is 23:00 UTC in winter, so the fee year turns an
    /// hour before the UTC year does.
    @Test
    void theFeeYearTurnsAtMidnightInSweden() {
        assertThat(ledgerAt(Instant.parse("2026-12-31T22:59:59Z")).currentYear()).isEqualTo(2026);
        assertThat(ledgerAt(Instant.parse("2026-12-31T23:00:00Z")).currentYear()).isEqualTo(2027);
        assertThat(ledgerAt(AFTER_MIDNIGHT_IN_SWEDEN).currentYear()).isEqualTo(2027);
    }

    /// A payment for 2026 covers the member and the household until
    /// midnight in Sweden. Half an hour later they are unpaid for 2027, and
    /// 2026 is history.
    @Test
    void aPaymentForTheOldYearStopsCountingWhenTheYearTurnsInSweden() {
        insertFee(johan, "INDIVIDUAL", 2026);
        insertFee(erik, "HOUSEHOLD", 2026);

        new TransactionTemplate(transactions).executeWithoutResult(status -> {
            List<Member> both = List.of(memberRepository.findById(johan).orElseThrow(),
                    memberRepository.findById(maria).orElseThrow());
            assertThat(ledgerAt(BEFORE_MIDNIGHT_IN_SWEDEN).thisYear(both))
                    .extracting(FeeStatus::year, FeeStatus::paid, FeeStatus::throughHousehold)
                    .containsExactly(tuple(2026, true, false), tuple(2026, true, true));

            FeeLedger newYear = ledgerAt(AFTER_MIDNIGHT_IN_SWEDEN);
            assertThat(newYear.thisYear(both))
                    .extracting(FeeStatus::year, FeeStatus::paid)
                    .containsExactly(tuple(2027, false), tuple(2027, false));
            assertThat(newYear.history(both.getFirst()))
                    .extracting(FeeStatus::year, FeeStatus::paid)
                    .containsExactly(tuple(2027, false), tuple(2026, true));
        });
    }

    /// A mark made at 00:30 on 1 January in Sweden is for the new year,
    /// beside the old year's payment, and undoing it leaves the old year's.
    @Test
    void aFeeMarkedJustAfterMidnightInSwedenIsForTheNewYear() {
        insertFee(johan, "INDIVIDUAL", 2026);
        FeeService stopped = new FeeService(feeRepository, memberRepository, ledgerAt(AFTER_MIDNIGHT_IN_SWEDEN),
                association, Clock.fixed(AFTER_MIDNIGHT_IN_SWEDEN, ZoneOffset.UTC));
        TransactionTemplate transaction = new TransactionTemplate(transactions);

        transaction.executeWithoutResult(status -> stopped.markPaid(new MemberId(johan),
                new FeeMark(FeeKind.INDIVIDUAL, null), new AdministratorId(firstAdministratorId())));
        assertThat(yearsPaidBy(johan)).containsExactly(2026, 2027);

        transaction.executeWithoutResult(status -> stopped.undo(new MemberId(johan)));
        assertThat(yearsPaidBy(johan)).containsExactly(2026);
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

    /// A member without an account cannot pay, so gets no payment instruction.
    /// The household payment covers them once it is made.
    @Test
    void aHouseholdMemberWithoutAnAccountGetsNoPaymentInstruction() throws Exception {
        long child = insertMember("Lisa Lindqvist");
        moveTo(child, household);

        mockMvc.perform(get("/api/admin/members/" + child).with(asAdministrator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fees[0].year").value(thisYear()))
                .andExpect(jsonPath("$.fees[0].paidAt").doesNotExist())
                .andExpect(jsonPath("$.fees[0].payment").doesNotExist());

        insertFee(erik, "HOUSEHOLD");

        mockMvc.perform(get("/api/admin/members/" + child).with(asAdministrator()))
                .andExpect(jsonPath("$.fees[0].paidAt").isNotEmpty())
                .andExpect(jsonPath("$.fees[0].throughHousehold").value(true));
    }

    @Test
    void theLatestPaidYearFollowsTheHouseholdRule() {
        insertFee(erik, "HOUSEHOLD");

        assertThat(fees.latestPaidYear(new MemberId(erik))).isEqualTo(thisYear());
        assertThat(fees.latestPaidYear(new MemberId(maria))).isEqualTo(thisYear());
        assertThat(fees.latestPaidYear(new MemberId(johan))).isNull();
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

    /// A `+` read back as a space, or braces read as a URI template in the
    /// redirect, would change or break the search the administrator came from.
    private void assertSearchSurvives(long member, String query, String encoded) throws Exception {
        String register = mockMvc.perform(get("/admin/medlemmar").param("q", query).with(asAdministrator()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(register).as(query).contains("href=\"/admin/medlemmar?q=" + encoded + "&amp;avgift=obetald\"");
        assertRedirect(markFromRegister(member, query), "/admin/medlemmar?q=" + encoded + "&avgift=obetald");
    }

    private MvcResult markFromRegister(long member) throws Exception {
        return markFromRegister(member, "Bergström");
    }

    private MvcResult markFromRegister(long member, String query) throws Exception {
        return mockMvc.perform(post("/admin/medlemmar/" + member + "/avgift")
                        .param("kind", "INDIVIDUAL")
                        .param("tillbaka", "lista")
                        .param("q", query)
                        .param("avgift", "obetald")
                        .with(asAdministrator())
                        .with(csrf()))
                .andReturn();
    }

    private String page(String path) throws Exception {
        return mockMvc.perform(get(path).with(asAdministrator())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    private Map<String, @Nullable Object> feeOf(long member) {
        return jdbc.sql("SELECT kind, amount_ore, year, marked_by FROM fee WHERE member_id = ?")
                .param(member)
                .query()
                .singleRow();
    }

    /// A ledger whose clock is stopped at `time`. The clock is in UTC, as the
    /// application's is, so the ledger has to find the year in Sweden itself.
    private FeeLedger ledgerAt(Instant time) {
        return new FeeLedger(feeRepository, accountRepository, association, Clock.fixed(time, ZoneOffset.UTC));
    }

    private List<@Nullable Integer> yearsPaidBy(long member) {
        return jdbc.sql("SELECT year FROM fee WHERE member_id = ? ORDER BY year").param(member)
                .query(Integer.class).list();
    }

    /// An administrator marks a household fee through the API, as the member's
    /// page offers for a member with or without a household.
    private void markHouseholdFee(long member) throws Exception {
        mockMvc.perform(post("/api/admin/members/" + member + "/fee")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\": \"HOUSEHOLD\"}")
                        .with(asAdministrator())
                        .with(csrf()))
                .andExpect(status().isNoContent());
    }

    /// An administrator edits the member and chooses a household for them.
    private void placeInHousehold(long member, String fullName, String email, long householdId) throws Exception {
        mockMvc.perform(put("/api/admin/members/" + member)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\": \"%s\", \"email\": \"%s\", \"householdId\": %d}"
                                .formatted(fullName, email, householdId))
                        .with(asAdministrator())
                        .with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.householdId").value(householdId));
    }

    private long householdOf(long member) {
        return jdbc.sql("SELECT household_id FROM member WHERE id = ?").param(member).query(Long.class).single();
    }

    /// @return the household the member's own payment this year names, or null for none
    private @Nullable Long feeHouseholdOf(long member) {
        return (Long) jdbc.sql("SELECT household_id FROM fee WHERE member_id = ? AND year = ?")
                .params(member, thisYear())
                .query()
                .singleRow()
                .get("household_id");
    }

    /// A member without an account has no `/api/member`, so the
    /// administrator's view of them is read instead.
    private void assertCoveredByHousehold(long member) throws Exception {
        mockMvc.perform(get("/api/admin/members/" + member).with(asAdministrator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fees[0].year").value(thisYear()))
                .andExpect(jsonPath("$.fees[0].paidAt").isNotEmpty())
                .andExpect(jsonPath("$.fees[0].throughHousehold").value(true));
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
