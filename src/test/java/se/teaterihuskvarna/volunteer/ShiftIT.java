package se.teaterihuskvarna.volunteer;

import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import se.teaterihuskvarna.IntegrationTestSupport;
import se.teaterihuskvarna.Swedish;
import se.teaterihuskvarna.content.ContentService;
import se.teaterihuskvarna.login.LoginKind;
import se.teaterihuskvarna.login.Mailer;
import se.teaterihuskvarna.login.SignedIn;

/// Proves volunteer shifts (R016, R017, R020) through both adapters.
///
/// - Only a member reaches the member side and only an administrator the
///   administrator side.
/// - An administrator can add a shift only to an upcoming published event.
/// - Booking is refused when the shift is full or has started, is idempotent,
///   and cancelling gives the place back.
/// - The reminder goes once, the day before, to each booked member's address,
///   at once to a booking made after the day's last run for a shift tomorrow,
///   and on the day itself to a booking the runs missed, with a text that
///   says today.
/// - A reminder that says tomorrow is not sent once that day has come, and
///   one that says today not once the shift has started.
/// - A past shift leaves the list of upcoming ones for the list of past ones.
/// - A member's last shift is the latest booked one that has started.
/// - The CSV file lists who booked, with a byte order mark.
class ShiftIT extends IntegrationTestSupport {

    private static final ZoneId SWEDEN = ZoneId.of("Europe/Stockholm");
    private static final String EVENT = "evenemang-kulturnatten";

    @Autowired
    private ShiftService shifts;

    @Autowired
    private ShiftRepository shiftRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private ContentService content;

    @Autowired
    private Mailer mailer;

    @Autowired
    private MessageSource messages;


    @Autowired
    private PlatformTransactionManager transactions;

    @Test
    void anonymousRequestsAreSentToLogInOrRefused() throws Exception {
        long id = insertShift(Instant.now().plus(Duration.ofDays(3)), 2);

        assertRedirect(mockMvc.perform(get("/medlem/volontar")).andReturn(), "/logga-in");
        assertRedirect(mockMvc.perform(get("/admin/volontarpass")).andReturn(), "/admin/logga-in");
        mockMvc.perform(get("/api/member/shifts")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/member/shifts/" + id + "/booking").with(csrf()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/shifts")).andExpect(status().isUnauthorized());
    }

    @Test
    void aMemberCannotReachTheAdministratorSide() throws Exception {
        long id = insertShift(Instant.now().plus(Duration.ofDays(3)), 2);
        RequestPostProcessor member = asMember(insertAccount("Karin Holm", "karin@example.test"));

        assertRedirect(mockMvc.perform(get("/admin/volontarpass/" + id + "/bokningar.csv").with(member))
                .andReturn(), "/admin/logga-in");
        mockMvc.perform(get("/api/admin/shifts/" + id + "/volunteers").with(member))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/admin/shifts/" + id).with(member).with(csrf()))
                .andExpect(status().isForbidden());

        assertThat(rowsIn("volunteer_shift")).isOne();
    }

    @Test
    void anAdministratorAddsAShiftToAnUpcomingEventOnly() throws Exception {
        LocalDate day = LocalDate.now(SWEDEN).plusDays(10);

        MvcResult created = mockMvc.perform(post("/admin/volontarpass").with(asAdministrator()).with(csrf())
                        .param("eventId", EVENT)
                        .param("task", "GARDEROB")
                        .param("startsAt", day + "T17:00")
                        .param("endsAt", day + "T21:30")
                        .param("places", "3"))
                .andExpect(flash().attributeExists("notice"))
                .andReturn();
        assertThat(locationOf(created)).startsWith("/admin/volontarpass/");
        assertThat(jdbc.sql("SELECT event_title FROM volunteer_shift").query(String.class).single())
                .isEqualTo("Kulturnatten på torget");

        mockMvc.perform(post("/api/admin/shifts").with(asAdministrator()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId": "evenemang-varshow", "task": "SERVERING",
                                 "startsAt": "%sT17:00", "endsAt": "%sT21:00", "places": 2}
                                """.formatted(day, day)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/admin/volontarpass").with(asAdministrator()).with(csrf())
                        .param("eventId", EVENT)
                        .param("task", "SERVERING")
                        .param("startsAt", day + "T21:00")
                        .param("endsAt", day + "T17:00")
                        .param("places", "2"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Passet måste sluta efter att det börjar.")));

        assertThat(rowsIn("volunteer_shift")).isOne();
    }

    /// A number of places no `int` holds, a year of five digits and a task that
    /// does not exist never become a `ShiftForm`. They are errors on their
    /// fields, and the page keeps what was typed.
    @Test
    void theFormShowsAValueItCannotReadOnItsField() throws Exception {
        LocalDate day = LocalDate.now(SWEDEN).plusDays(10);

        String page = bodyOf(mockMvc.perform(post("/admin/volontarpass").with(asAdministrator()).with(csrf())
                        .param("eventId", EVENT)
                        .param("task", "GARDEROB")
                        .param("startsAt", day + "T17:00")
                        .param("endsAt", "1" + day + "T21:30")
                        .param("places", "99999999999"))
                .andExpect(status().isOk())
                .andReturn());

        assertUnreadable(page, "places", "99999999999", text("shift.places.invalid"));
        assertUnreadable(page, "endsAt", "1" + day + "T21:30", text("shift.endsAt.invalid"));
        assertThat(page).contains("<option value=\"" + EVENT + "\" selected>", "value=\"GARDEROB\" required checked>",
                        "value=\"" + day + "T17:00\"")
                .doesNotContain("startsAt-error");

        String noSuchTask = bodyOf(mockMvc.perform(post("/admin/volontarpass").with(asAdministrator())
                        .with(csrf())
                        .param("eventId", EVENT)
                        .param("task", "DISKA")
                        .param("startsAt", day + "T17:00")
                        .param("endsAt", day + "T21:30")
                        .param("places", "3"))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(noSuchTask).contains("<li><a href=\"#task\">" + text("shift.task.invalid") + "</a></li>",
                "<span class=\"field-error\">" + text("shift.task.invalid") + "</span>", "value=\"3\"");
        mockMvc.perform(post("/api/admin/shifts").with(asAdministrator()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"eventId": "%s", "task": "GARDEROB",
                                 "startsAt": "%sT17:00", "endsAt": "%sT21:00", "places": 99999999999}
                                """.formatted(EVENT, day, day)))
                .andExpect(status().isBadRequest());

        assertThat(rowsIn("volunteer_shift")).isZero();
    }

    /// The browser stops a number over the limit before it is sent, with the
    /// same limit the service has.
    @Test
    void thePlacesFieldCarriesTheLimitTheServiceHas() throws Exception {
        mockMvc.perform(get("/admin/volontarpass").with(asAdministrator()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("max=\"" + ShiftForm.PLACES_MAX + "\"")));
    }

    @Test
    void bookingIsIdempotentAndCancellingGivesThePlaceBack() throws Exception {
        long id = insertShift(Instant.now().plus(Duration.ofDays(3)), 1);
        RequestPostProcessor karin = asMember(insertAccount("Karin Holm", "karin@example.test"));
        RequestPostProcessor olle = asMember(insertAccount("Olle Berg", "olle@example.test"));

        mockMvc.perform(post("/api/member/shifts/" + id + "/booking").with(karin).with(csrf()))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/member/shifts/" + id + "/booking").with(karin).with(csrf()))
                .andExpect(status().isOk());
        assertThat(rowsIn("volunteer_booking")).isOne();

        mockMvc.perform(post("/api/member/shifts/" + id + "/booking").with(olle).with(csrf()))
                .andExpect(status().isConflict());
        mockMvc.perform(post("/medlem/volontar/" + id + "/boka").with(olle).with(csrf()))
                .andExpect(flash().attribute("error", "Passet blev fullbokat innan du hann boka."));
        mockMvc.perform(get("/api/member/shifts").with(olle))
                .andExpect(jsonPath("$[0].placesLeft").value(0))
                .andExpect(jsonPath("$[0].booked").value(false));

        mockMvc.perform(post("/medlem/volontar/" + id + "/avboka").with(karin).with(csrf()))
                .andExpect(flash().attribute("notice", "Du har avbokat passet."));
        mockMvc.perform(post("/medlem/volontar/" + id + "/boka").with(olle).with(csrf()))
                .andExpect(flash().attribute("notice", "Du har bokat passet."));
        mockMvc.perform(get("/medlem/volontar").with(olle))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Avboka")));
    }

    @Test
    void aShiftThatHasStartedCannotBeBookedOrCancelled() throws Exception {
        long id = insertShift(Instant.now().minus(Duration.ofMinutes(5)), 3);
        long accountId = insertAccount("Karin Holm", "karin@example.test");
        jdbc.sql("INSERT INTO volunteer_booking (shift_id, member_id, created_at) VALUES (?, ?, now())")
                .param(id).param(memberOf(accountId)).update();
        RequestPostProcessor karin = asMember(accountId);
        RequestPostProcessor olle = asMember(insertAccount("Olle Berg", "olle@example.test"));

        mockMvc.perform(post("/api/member/shifts/" + id + "/booking").with(olle).with(csrf()))
                .andExpect(status().isConflict());
        mockMvc.perform(delete("/api/member/shifts/" + id + "/booking").with(karin).with(csrf()))
                .andExpect(status().isConflict());
        assertThat(rowsIn("volunteer_booking")).isOne();
    }

    @Test
    void theReminderGoesOnceTheDayBefore() {
        Instant tomorrowEvening = LocalDate.now(SWEDEN).plusDays(1).atTime(18, 0).atZone(SWEDEN).toInstant();
        long tomorrow = insertShift(tomorrowEvening, 3);
        long later = insertShift(tomorrowEvening.plus(Duration.ofDays(2)), 3);
        long karin = memberOf(insertAccount("Karin Holm", "karin@example.test"));
        // Booked at noon, before the day's last run. A booking after it is
        // reminded at once, so on the real clock this failed from 21:00.
        LocalDateTime noon = LocalDate.now(SWEDEN).atTime(12, 0);
        book(noon, tomorrow, karin);
        book(noon, later, karin);

        assertThat(shifts.sendReminders()).isOne();
        SimpleMailMessage mail = awaitMail();
        assertThat(mail.getTo()).containsExactly("karin@example.test");
        assertThat(mail.getSubject()).contains("Kulturnatten på torget");
        assertThat(mail.getText()).contains("Garderob").contains("18:00");

        forgetMails();
        assertThat(shifts.sendReminders()).isZero();
        assertNoMail();
    }

    /// After the day's last run, a booking for tomorrow is reminded at once,
    /// since the next run may come after an early shift has started. Before
    /// it, the runs do it.
    @Test
    void aBookingAfterTheLastRunForTomorrowIsRemindedAtOnce() {
        LocalDate today = LocalDate.now(SWEDEN);
        Instant earlyTomorrow = today.plusDays(1).atTime(8, 0).atZone(SWEDEN).toInstant();
        long early = insertShift(earlyTomorrow, 3);
        long karin = memberOf(insertAccount("Karin Holm", "karin@example.test"));
        long nils = memberOf(insertAccount("Nils Berg", "nils@example.test"));

        book(today.atTime(20, 59), early, nils);
        assertNoMail();

        book(today.atTime(21, 5), early, karin);
        SimpleMailMessage mail = awaitMail();
        assertThat(mail.getTo()).containsExactly("karin@example.test");
        assertThat(mail.getText()).contains("08:00");
        assertThat(jdbc.sql("SELECT reminded_at IS NOT NULL FROM volunteer_booking WHERE member_id = ?")
                .param(karin).query(Boolean.class).single()).isTrue();
    }

    @Test
    void aShiftLaterTodayIsRemindedOnlyIfBookedBeforeToday() {
        Instant now = Instant.now();
        Instant startOfToday = LocalDate.now(SWEDEN).atStartOfDay(SWEDEN).toInstant();
        Instant startsAt = now.plus(Duration.ofMinutes(5));
        Assumptions.assumeTrue(startsAt.isBefore(startOfToday.plus(Duration.ofDays(1))),
                "needs five minutes left of today in Sweden");
        long shift = insertShift(startsAt, 3);
        long late = memberOf(insertAccount("Karin Holm", "karin@example.test"));
        long sameDay = memberOf(insertAccount("Nils Berg", "nils@example.test"));
        insertBooking(shift, late, startOfToday.minus(Duration.ofMinutes(30)));
        insertBooking(shift, sameDay, now);

        assertThat(shifts.sendReminders()).isOne();
        assertThat(awaitMail().getTo()).containsExactly("karin@example.test");
    }

    /// "i morgon" is wrong from midnight, so the outbox deletes the mail
    /// unsent from then.
    @Test
    void aReminderThatSaysTomorrowIsNotSentOnTheShiftsOwnDay() {
        LocalDate today = LocalDate.now(SWEDEN);
        long shift = insertShift(today.plusDays(1).atTime(18, 0).atZone(SWEDEN).toInstant(), 3);
        long karin = memberOf(insertAccount("Karin Holm", "karin@example.test"));
        book(today.atTime(12, 0), shift, karin);
        refuseEveryMail();

        assertThat(sendReminders(today.atTime(13, 0))).isOne();
        SimpleMailMessage mail = awaitMails(1).getFirst();

        assertThat(mail.getSubject()).contains("i morgon");
        assertThat(mail.getText()).contains("volontärpass i morgon.");
        assertThat(notAfterOfTheMailInTheOutbox()).isEqualTo(today.plusDays(1).atStartOfDay(SWEDEN).toInstant());
    }

    /// The runs missed the booking the day before. The clock stands on the
    /// morning of the shift's own day.
    @Test
    void aReminderOnTheShiftsOwnDaySaysTodayAndIsNotSentAfterTheShiftStarts() {
        LocalDate shiftDay = LocalDate.now(SWEDEN).plusDays(1);
        Instant startsAt = shiftDay.atTime(18, 0).atZone(SWEDEN).toInstant();
        long shift = insertShift(startsAt, 3);
        long karin = memberOf(insertAccount("Karin Holm", "karin@example.test"));
        insertBooking(shift, karin, shiftDay.atStartOfDay(SWEDEN).toInstant().minus(Duration.ofHours(3)));
        refuseEveryMail();

        assertThat(sendReminders(shiftDay.atTime(9, 0))).isOne();
        SimpleMailMessage mail = awaitMails(1).getFirst();

        assertThat(mail.getSubject()).contains("i dag").doesNotContain("i morgon");
        assertThat(mail.getText()).contains("volontärpass i dag.").contains("18:00").doesNotContain("i morgon");
        assertThat(notAfterOfTheMailInTheOutbox()).isEqualTo(startsAt);
    }

    @Test
    void aPastShiftMovesToThePastListInBothAdapters() throws Exception {
        long yesterday = insertShift(Instant.now().minus(Duration.ofDays(1)).minus(Duration.ofHours(1)), 3);
        long upcoming = insertShift(Instant.now().plus(Duration.ofDays(3)), 3);

        assertThat(shifts.past()).extracting(ShiftDetails::id).containsExactly(yesterday);
        assertThat(shifts.list()).extracting(ShiftDetails::id).containsExactly(upcoming);
        mockMvc.perform(get("/api/admin/shifts/past").with(asAdministrator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(yesterday));
        mockMvc.perform(get("/admin/volontarpass").with(asAdministrator()))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("/admin/volontarpass/" + yesterday)));
    }

    @Test
    void theUpcomingShiftsHaveAHeadingAsThePastOnesDo() throws Exception {
        String empty = mockMvc.perform(get("/admin/volontarpass").with(asAdministrator()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        long upcoming = insertShift(Instant.now().plus(Duration.ofDays(3)), 3);
        String listed = mockMvc.perform(get("/admin/volontarpass").with(asAdministrator()))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(empty.replaceAll("\\s+", " "))
                .containsSubsequence("<h2>Kommande pass</h2> <p>Inga kommande pass.</p>", "<h2>Tidigare pass</h2>");
        assertThat(listed).containsSubsequence("<h2>Kommande pass</h2>", "/admin/volontarpass/" + upcoming,
                "<h2>Lägg till ett pass</h2>", "<h2>Tidigare pass</h2>");
    }

    @Test
    void theAdministratorStartPageListsOnlyUpcomingShiftsWithFreePlaces() throws Exception {
        long full = insertShift(Instant.now().plus(Duration.ofDays(2)), 1);
        long open = insertShift(Instant.now().plus(Duration.ofDays(3)), 3);
        long past = insertShift(Instant.now().minus(Duration.ofDays(2)), 3);
        // Started earlier today: the shift list starts at midnight, so only the
        // start time keeps this one off.
        Instant midnight = LocalDate.now(SWEDEN).atStartOfDay(SWEDEN).toInstant();
        Instant anHourAgo = Instant.now().minus(Duration.ofHours(1));
        long startedToday = insertShift(anHourAgo.isAfter(midnight) ? anHourAgo : midnight, 3);
        long anna = memberOf(insertAccount("Anna Först", "anna@example.test"));
        insertBooking(full, anna, Instant.now());
        insertBooking(open, anna, Instant.now());

        String page = mockMvc.perform(get("/admin").with(asAdministrator())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(page).contains("/admin/volontarpass/" + open + "\"", "1 av 3")
                .doesNotContain("/admin/volontarpass/" + full + "\"")
                .doesNotContain("/admin/volontarpass/" + past + "\"")
                .doesNotContain("/admin/volontarpass/" + startedToday + "\"");
        mockMvc.perform(get("/api/admin/shifts/needing-volunteers").with(asAdministrator()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(open));

        insertBooking(open, memberOf(insertAccount("Bo Andra", "bo@example.test")), Instant.now());
        insertBooking(open, memberOf(insertAccount("Cia Tredje", "cia@example.test")), Instant.now());
        mockMvc.perform(get("/admin").with(asAdministrator()))
                .andExpect(content().string(containsString("Inget kommande pass saknar volontärer.")));
    }

    @Test
    void theLastShiftIsTheLatestThatHasStarted() {
        Instant past = Instant.now().minus(Duration.ofDays(20));
        long earlier = insertShift(past.minus(Duration.ofDays(30)), 3);
        long later = insertShift(past, 3);
        long future = insertShift(Instant.now().plus(Duration.ofDays(3)), 3);
        long karin = memberOf(insertAccount("Karin Holm", "karin@example.test"));
        long nils = memberOf(insertAccount("Nils Berg", "nils@example.test"));
        insertBooking(earlier, karin, past.minus(Duration.ofDays(40)));
        insertBooking(later, karin, past.minus(Duration.ofDays(10)));
        insertBooking(future, karin, Instant.now());
        insertBooking(future, nils, Instant.now());

        assertThat(shifts.lastShift(karin)).isEqualTo(LocalDate.ofInstant(past, SWEDEN));
        assertThat(shifts.lastShift(nils)).isNull();
    }

    @Test
    void theDeletionWarningCountsTheBookings() throws Exception {
        long id = insertShift(Instant.now().plus(Duration.ofDays(3)), 3);
        assertThat(deletePage(id)).contains("Passet tas bort. Ingen har bokat det.");

        insertBooking(id, memberOf(insertAccount("Anna Först", "anna@example.test")), Instant.now());
        assertThat(deletePage(id)).contains("Passet och dess bokning tas bort. Volontären får inget meddelande.");

        insertBooking(id, memberOf(insertAccount("Karin Holm", "karin@example.test")), Instant.now());
        assertThat(deletePage(id))
                .contains("Passet och dess 2 bokningar tas bort. Volontärerna får inget meddelande.");
    }

    /// The shift's page has a link, not a button that deletes. The page it leads
    /// to says which shift goes, and only its button deletes.
    @Test
    void deletingOnThePageAsksFirst() throws Exception {
        long id = insertShift(Instant.now().plus(Duration.ofDays(3)), 3);
        insertBooking(id, memberOf(insertAccount("Anna Först", "anna@example.test")), Instant.now());
        String path = "/admin/volontarpass/" + id + "/ta-bort";

        assertThat(bodyOf(mockMvc.perform(get("/admin/volontarpass/" + id).with(asAdministrator())).andReturn()))
                .contains("<a href=\"" + path + "\">")
                .doesNotContain("action=\"" + path + "\"");

        String page = deletePage(id);
        assertThat(page).contains(
                "<h1>" + text("adminShifts.delete") + "</h1>",
                "Garderob, Kulturnatten på torget",
                "<form method=\"post\" action=\"" + path + "\">",
                "<a href=\"/admin/volontarpass/" + id + "\">" + text("adminShifts.delete.cancel") + "</a>");
        assertThat(rowsIn("volunteer_shift")).isOne();
        assertThat(rowsIn("volunteer_booking")).isOne();
        mockMvc.perform(get("/admin/volontarpass/999999/ta-bort").with(asAdministrator()))
                .andExpect(status().isNotFound());
        assertRedirect(mockMvc.perform(get(path).with(asMember(insertAccount("Karin Holm", "karin@example.test"))))
                .andReturn(), "/admin/logga-in");

        mockMvc.perform(post(path).with(asAdministrator()).with(csrf()))
                .andExpect(flash().attribute("notice", text("adminShifts.deleted")));
        assertThat(rowsIn("volunteer_shift")).isZero();
        assertThat(rowsIn("volunteer_booking")).isZero();
    }

    @Test
    void theCsvFileListsWhoBookedTheSameThroughBothAdapters() throws Exception {
        long id = insertShift(Instant.now().plus(Duration.ofDays(3)), 3);
        shifts.book(id, memberOf(insertAccount("=Karin Holm", "karin@example.test")));

        MvcResult page = mockMvc.perform(get("/admin/volontarpass/" + id + "/bokningar.csv")
                        .with(asAdministrator()))
                .andExpect(status().isOk())
                .andReturn();
        MvcResult api = mockMvc.perform(get("/api/admin/shifts/" + id + "/volunteers.csv").with(asAdministrator()))
                .andExpect(status().isOk())
                .andReturn();

        byte[] bytes = page.getResponse().getContentAsByteArray();
        assertThat(api.getResponse().getContentAsByteArray()).isEqualTo(bytes);
        assertThat(bytes).startsWith(0xEF, 0xBB, 0xBF);
        List<String> lines = new String(bytes, StandardCharsets.UTF_8).substring(1).lines().toList();
        assertThat(lines.get(0)).isEqualTo("Namn;E-post;Telefon;Bokat");
        assertThat(lines.get(1)).startsWith("'=Karin Holm;karin@example.test;");
    }

    @Test
    void theStartPageListsOnlyTheShiftsTheMemberBooked() throws Exception {
        long booked = insertShift(Instant.now().plus(Duration.ofDays(3)), 2);
        insertShift(Instant.now().plus(Duration.ofDays(5)), 2);
        long account = insertAccount("Karin Holm", "karin@example.test");
        RequestPostProcessor member = asMember(account);
        String none = messages.getMessage("member.shifts.none", null, Swedish.LOCALE);

        String before = mockMvc.perform(get("/medlem").with(member)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(before).contains(none).doesNotContain("Kulturnatten på torget");

        insertBooking(booked, memberOf(account), Instant.now());
        String after = mockMvc.perform(get("/medlem").with(member)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(after).doesNotContain(none).contains("href=\"/medlem/volontar#pass-" + booked + "\"");
        assertThat(after.split("Kulturnatten på torget", -1)).hasSize(2);
        mockMvc.perform(get("/api/member/shifts/booked").with(member))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].id", contains((int) booked)));

        String shiftsPage = mockMvc.perform(get("/medlem/volontar").with(member)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertThat(shiftsPage).contains("id=\"pass-" + booked + "\"")
                .contains("href=\"/medlem\" aria-current=\"true\">Mina sidor</a>").doesNotContain("Översikt");
    }

    private String text(String key) {
        return messages.getMessage(key, null, Swedish.LOCALE);
    }

    private String deletePage(long id) throws Exception {
        return bodyOf(mockMvc.perform(get("/admin/volontarpass/" + id + "/ta-bort").with(asAdministrator()))
                .andExpect(status().isOk())
                .andReturn());
    }

    private long insertShift(Instant startsAt, int places) {
        return jdbc.sql("""
                INSERT INTO volunteer_shift (event_id, event_title, event_slug, task, starts_at, ends_at, places,
                    created_at)
                VALUES (?, 'Kulturnatten på torget', 'kulturnatten', 'GARDEROB', ?, ?, ?, now())
                RETURNING id
                """)
                .param(EVENT)
                .param(Timestamp.from(startsAt))
                .param(Timestamp.from(startsAt.plus(Duration.ofHours(4))))
                .param(places)
                .query(Long.class)
                .single();
    }

    /// Books through a service whose clock is stopped at `time` in Sweden. Made
    /// by hand, it has no transaction of its own, so the test gives it one.
    private void book(LocalDateTime time, long shiftId, long memberId) {
        ShiftService stopped = stoppedAt(time);
        new TransactionTemplate(transactions).executeWithoutResult(status -> stopped.book(shiftId, memberId));
    }

    /// @return how many reminders the job queued, run with the clock stopped at `time` in Sweden
    private int sendReminders(LocalDateTime time) {
        ShiftService stopped = stoppedAt(time);
        return requireNonNull(new TransactionTemplate(transactions).execute(status -> stopped.sendReminders()));
    }

    private ShiftService stoppedAt(LocalDateTime time) {
        return new ShiftService(shiftRepository, bookingRepository, content, mailer, messages,
                Clock.fixed(time.atZone(SWEDEN).toInstant(), SWEDEN));
    }

    private void insertBooking(long shiftId, long memberId, Instant createdAt) {
        jdbc.sql("INSERT INTO volunteer_booking (shift_id, member_id, created_at) VALUES (?, ?, ?)")
                .param(shiftId).param(memberId).param(Timestamp.from(createdAt)).update();
    }

    private long memberOf(long accountId) {
        return jdbc.sql("SELECT member_id FROM account WHERE id = ?").param(accountId).query(Long.class).single();
    }

    private RequestPostProcessor asMember(long accountId) {
        String email = jdbc.sql("SELECT email FROM account WHERE id = ?").param(accountId).query(String.class)
                .single();
        return user(new SignedIn(LoginKind.MEMBER, accountId, email, "Medlem"));
    }

    private RequestPostProcessor asAdministrator() {
        return user(new SignedIn(LoginKind.ADMINISTRATOR, firstAdministratorId(), firstAdministratorEmail,
                "Ada Admin"));
    }
}
