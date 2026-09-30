package se.teaterihuskvarna.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;

/// The event the start page leads with (R001): the next to start today, else
/// the one that started last today, else the first after today.
class NextEventTest {

    private static final ZoneId SWEDEN = ZoneId.of("Europe/Stockholm");
    private static final LocalDate TODAY = LocalDate.of(2026, 10, 10);

    private final Event morning = event("morgon", TODAY, 10);
    private final Event evening = event("kvall", TODAY, 19);
    private final Event tomorrow = event("imorgon", TODAY.plusDays(1), 12);
    private final List<Event> upcoming = List.of(morning, evening, tomorrow);

    @Test
    void theNextToStartTodayLeads() {
        assertThat(ContentService.lead(upcoming, at(8))).contains(morning);
        assertThat(ContentService.lead(upcoming, at(15))).contains(evening);
    }

    @Test
    void theLastStartedTodayLeadsUntilMidnight() {
        assertThat(ContentService.lead(upcoming, at(20))).contains(evening);
        assertThat(ContentService.lead(List.of(morning, tomorrow), at(15))).contains(morning);
        assertThat(ContentService.lead(List.of(evening), at(23))).contains(evening);
    }

    @Test
    void withNothingTodayTheFirstAfterTodayLeads() {
        assertThat(ContentService.lead(List.of(tomorrow), at(15))).contains(tomorrow);
        assertThat(ContentService.lead(List.of(), at(15))).isEmpty();
    }

    private static Instant at(int hour) {
        return TODAY.atTime(hour, 0).atZone(SWEDEN).toInstant();
    }

    private static Event event(String slug, LocalDate day, int hour) {
        return new Event(slug, slug, slug, null, day.atTime(hour, 0).atZone(SWEDEN).toInstant(), null, null, null,
                "", List.of(), null);
    }
}
