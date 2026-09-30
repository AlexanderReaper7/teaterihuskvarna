package se.teaterihuskvarna.volunteer;

import jakarta.validation.Valid;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.jspecify.annotations.Nullable;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import se.teaterihuskvarna.Swedish;
import se.teaterihuskvarna.content.ContentService;
import se.teaterihuskvarna.content.Event;
import se.teaterihuskvarna.content.Perspective;
import se.teaterihuskvarna.export.Csv;
import se.teaterihuskvarna.export.CsvFile;
import se.teaterihuskvarna.login.Email;
import se.teaterihuskvarna.login.Mailer;
import se.teaterihuskvarna.member.MemberChanged;

/// Volunteer shifts at performances: R016 booking, R017 the reminder, R020 the
/// list and export. `docs/decisions/0024-volunteer-shifts.md`.
///
/// An administrator adds shifts to an upcoming Sanity event. A member books
/// and cancels until the shift starts. Booking locks the shift row and counts
/// in the same transaction, so the places hold however many press at once.
///
/// The member-side methods take the member's id, not the account's. The
/// adapters get it from `MemberService.findByAccount`.
@Service
@Validated
@Transactional(readOnly = true)
public class ShiftService {

    /// Sweden, where every shift takes place.
    static final ZoneId SWEDEN = ZoneId.of("Europe/Stockholm");

    /// The time of [ShiftReminders]' last run of the day.
    static final LocalTime LAST_RUN = LocalTime.of(21, 0);

    private static final DateTimeFormatter CSV_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(SWEDEN);
    private static final DateTimeFormatter MAIL_TIME = DateTimeFormatter
            .ofPattern("EEEE d MMMM 'kl.' HH:mm", Swedish.LOCALE).withZone(SWEDEN);
    private static final DateTimeFormatter MAIL_END = DateTimeFormatter.ofPattern("HH:mm").withZone(SWEDEN);

    private final ShiftRepository shifts;
    private final BookingRepository bookings;
    private final ContentService content;
    private final Mailer mailer;
    private final MessageSource messages;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    ShiftService(ShiftRepository shifts, BookingRepository bookings, ContentService content, Mailer mailer,
            MessageSource messages, Clock clock, ApplicationEventPublisher events) {
        this.shifts = shifts;
        this.bookings = bookings;
        this.content = content;
        this.mailer = mailer;
        this.messages = messages;
        this.clock = clock;
        this.events = events;
    }

    /// @param memberId the member asking
    /// @return the shifts that have not started, earliest first
    public List<MemberShift> upcoming(long memberId) {
        List<Shift> found = shifts.findStartingFrom(clock.instant());
        Map<Long, Long> counts = counts(found);
        Set<Long> mine = new HashSet<>(bookings.findShiftIdsByMemberId(memberId));
        return found.stream()
                .map(shift -> memberShift(shift, counts.getOrDefault(shift.getId(), 0L), mine.contains(shift.getId())))
                .toList();
    }

    /// Takes a place. The shift row stays locked from the read until the
    /// commit, so a second booking for the same shift waits and then counts
    /// the first one.
    ///
    /// @param shiftId  the shift
    /// @param memberId the member booking
    /// @return whether a place was taken or the member already had one
    /// @throws NoSuchShift if no shift has the id
    /// @throws ShiftStarted if the shift has started
    /// @throws ShiftFull if every place is taken
    @Transactional
    public BookingOutcome book(long shiftId, long memberId) {
        Shift shift = shifts.lockById(shiftId).orElseThrow(() -> new NoSuchShift("no shift " + shiftId));
        if (bookings.existsByShiftIdAndMemberId(shiftId, memberId)) {
            return BookingOutcome.ALREADY_BOOKED;
        }
        if (!clock.instant().isBefore(shift.getStartsAt())) {
            throw new ShiftStarted("shift " + shiftId + " has started");
        }
        if (shift.placesLeft(bookings.countByShiftId(shiftId)) == 0) {
            throw new ShiftFull("shift " + shiftId + " is full");
        }
        Booking booking = bookings.save(new Booking(shiftId, memberId, clock.instant()));
        if (afterTheLastRunForTomorrow(shift)) {
            remind(booking, shift);
        }
        return BookingOutcome.BOOKED;
    }

    /// A booking made after the day's last reminder run, for a shift tomorrow,
    /// gets its reminder at once: the next run is on the shift's own day, and
    /// may come after the shift has started, such as one at 08:00.
    private boolean afterTheLastRunForTomorrow(Shift shift) {
        ZonedDateTime now = clock.instant().atZone(SWEDEN);
        LocalDate shiftDay = LocalDate.ofInstant(shift.getStartsAt(), SWEDEN);
        return shiftDay.equals(now.toLocalDate().plusDays(1)) && !now.toLocalTime().isBefore(LAST_RUN);
    }

    /// Gives a place back, which is allowed until the shift starts.
    ///
    /// @param shiftId  the shift
    /// @param memberId the member cancelling
    /// @return whether the member had a place to give back
    /// @throws NoSuchShift if no shift has the id
    /// @throws ShiftStarted if the shift has started
    @Transactional
    public boolean cancel(long shiftId, long memberId) {
        Shift shift = shifts.findById(shiftId).orElseThrow(() -> new NoSuchShift("no shift " + shiftId));
        if (!clock.instant().isBefore(shift.getStartsAt())) {
            throw new ShiftStarted("shift " + shiftId + " has started");
        }
        return bookings.deleteBooking(shiftId, memberId) > 0;
    }

    /// @return the events a shift can be added to: the upcoming published ones
    public List<Event> events() {
        return content.upcomingEvents(null, Perspective.PUBLISHED);
    }

    /// @return every shift from the start of today in Sweden on, earliest first
    public List<ShiftDetails> list() {
        return details(shifts.findStartingFrom(startOfToday()));
    }

    /// The shifts [#list] no longer shows, so their bookings can still be
    /// found and exported (R020).
    ///
    /// @return every shift that started before today in Sweden, latest first
    public List<ShiftDetails> past() {
        return details(shifts.findStartingBefore(startOfToday()));
    }

    /// @param id the shift
    /// @return the shift
    /// @throws NoSuchShift if no shift has the id
    public ShiftDetails details(long id) {
        Shift shift = shifts.findById(id).orElseThrow(() -> new NoSuchShift("no shift " + id));
        return ShiftDetails.of(shift, bookings.countByShiftId(id));
    }

    /// Adds a shift. The event's title and slug are copied onto it.
    ///
    /// @param form the event, the work, the times and the places
    /// @return the shift as stored
    /// @throws NoSuchEvent if the event is not an upcoming published one
    /// @throws jakarta.validation.ConstraintViolationException if the form breaks a constraint
    @Transactional
    public ShiftDetails create(@Valid ShiftForm form) {
        Event event = events().stream()
                .filter(one -> one.id().equals(form.eventId()))
                .findFirst()
                .orElseThrow(() -> new NoSuchEvent("no upcoming event " + form.eventId()));
        Shift shift = shifts.save(new Shift(event.id(), event.title(), event.slug(),
                Objects.requireNonNull(form.task()), instant(Objects.requireNonNull(form.startsAt())),
                instant(Objects.requireNonNull(form.endsAt())), Objects.requireNonNull(form.places()),
                clock.instant()));
        return ShiftDetails.of(shift, 0);
    }

    /// Deletes a shift and, through the foreign key, every booking of it.
    ///
    /// @param id the shift
    /// @throws NoSuchShift if no shift has the id
    @Transactional
    public void delete(long id) {
        Shift shift = shifts.findById(id).orElseThrow(() -> new NoSuchShift("no shift " + id));
        if (!shift.getStartsAt().isAfter(clock.instant())) {
            for (Volunteer volunteer : bookings.findVolunteers(id)) {
                events.publishEvent(new MemberChanged(volunteer.memberId()));
            }
        }
        shifts.delete(shift);
    }

    /// @param id the shift
    /// @return who booked it, in the order they did
    /// @throws NoSuchShift if no shift has the id
    public List<Volunteer> volunteers(long id) {
        if (!shifts.existsById(id)) {
            throw new NoSuchShift("no shift " + id);
        }
        return bookings.findVolunteers(id);
    }

    /// The same rows as [#volunteers], as a CSV file.
    ///
    /// @param id the shift
    /// @return the file and a name for it
    /// @throws NoSuchShift if no shift has the id
    public CsvFile volunteersCsv(long id) {
        List<List<String>> rows = volunteers(id).stream()
                .map(volunteer -> List.of(
                        volunteer.fullName(),
                        Objects.requireNonNullElse(volunteer.email(), ""),
                        Objects.requireNonNullElse(volunteer.phone(), ""),
                        CSV_TIME.format(volunteer.bookedAt())))
                .toList();
        List<String> header = List.of(text("shift.csv.name"), text("shift.csv.email"), text("shift.csv.phone"),
                text("shift.csv.bookedAt"));
        return new CsvFile("volontarpass-" + id + ".csv", Csv.write(header, rows));
    }

    /// For the member's Brevo contact, from which the association picks
    /// volunteers for a mailing (R022). A booked shift counts once it starts.
    ///
    /// @param memberId a member
    /// @return the day in Sweden the latest shift the member was booked on started, or null
    public @Nullable LocalDate lastShift(long memberId) {
        Instant start = bookings.findLastShiftStart(memberId, clock.instant());
        return start == null ? null : LocalDate.ofInstant(start, SWEDEN);
    }

    /// Tells the contacts of the members booked on each shift that has
    /// started since the last run, since their LAST_SHIFT changes with the
    /// start and not with anything written. Each shift is reported once.
    ///
    /// @return how many shifts were reported
    @Transactional
    public int reportStartedShifts() {
        List<Shift> started = shifts.findStartedUnreported(clock.instant());
        for (Shift shift : started) {
            for (Volunteer volunteer : bookings.findVolunteers(shift.getId())) {
                events.publishEvent(new MemberChanged(volunteer.memberId()));
            }
            shift.startReported();
        }
        return started.size();
    }

    /// R017: mails every member booked on a shift that starts tomorrow in
    /// Sweden, once. [ShiftReminders] calls it every hour from 09:00 to 21:00,
    /// so a booking made today for tomorrow still gets its reminder. A booking
    /// made after the last run is reminded when it is made ([#book]); one the
    /// runs missed, such as during a restart, gets it on the shift's own day if
    /// the shift has not started. A booking made on the shift's own day gets none: the
    /// member booked knowing it was today. A member without an account has no
    /// address and is marked as reminded all the same, so the job does not
    /// look at the row again.
    ///
    /// @return how many reminders were queued
    @Transactional
    public int sendReminders() {
        LocalDate today = LocalDate.now(clock.withZone(SWEDEN));
        Instant startOfToday = today.atStartOfDay(SWEDEN).toInstant();
        Instant startOfTomorrow = today.plusDays(1).atStartOfDay(SWEDEN).toInstant();
        Instant endOfTomorrow = today.plusDays(2).atStartOfDay(SWEDEN).toInstant();
        int sent = 0;
        for (Booking booking : bookings.findUnreminded(clock.instant(), startOfToday, startOfTomorrow,
                endOfTomorrow)) {
            sent += remind(booking, shifts.findById(booking.getShiftId()).orElseThrow());
        }
        return sent;
    }

    /// @return how many mails were queued: one, or none for a member without an account
    private int remind(Booking booking, Shift shift) {
        int sent = 0;
        for (String email : bookings.findEmail(booking.getMemberId())) {
            mailer.send(new Email(email), text("shift.reminder.subject", shift.getEventTitle()),
                    text("shift.reminder.body", text("shift.task." + shift.getTask().name()),
                            shift.getEventTitle(), MAIL_TIME.format(shift.getStartsAt()),
                            MAIL_END.format(shift.getEndsAt())));
            sent++;
        }
        booking.reminded(clock.instant());
        return sent;
    }

    private Instant startOfToday() {
        return LocalDate.now(clock.withZone(SWEDEN)).atStartOfDay(SWEDEN).toInstant();
    }

    private List<ShiftDetails> details(List<Shift> found) {
        Map<Long, Long> counts = counts(found);
        return found.stream().map(shift -> ShiftDetails.of(shift, counts.getOrDefault(shift.getId(), 0L))).toList();
    }

    private Map<Long, Long> counts(List<Shift> list) {
        if (list.isEmpty()) {
            return Map.of();
        }
        return bookings.countByShiftIds(list.stream().map(Shift::getId).toList()).stream()
                .collect(Collectors.toMap(BookingCount::shiftId, BookingCount::booked));
    }

    private static MemberShift memberShift(Shift shift, long booked, boolean mine) {
        return new MemberShift(shift.getId(), shift.getEventTitle(), shift.getEventSlug(), shift.getTask(),
                shift.getStartsAt(), shift.getEndsAt(), shift.placesLeft(booked), mine);
    }

    private static Instant instant(LocalDateTime time) {
        return time.atZone(SWEDEN).toInstant();
    }

    private String text(String key, Object... args) {
        return messages.getMessage(key, args, Swedish.LOCALE);
    }
}
