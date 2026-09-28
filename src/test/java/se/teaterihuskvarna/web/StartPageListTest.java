package se.teaterihuskvarna.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import se.teaterihuskvarna.content.Event;

/// The start page's list below the lead event keeps another event that
/// starts at the same moment, and drops the lead one and those before it.
class StartPageListTest {

    private final Event morning = event("morgon", "2026-10-10T08:00:00Z");
    private final Event evening = event("kvall", "2026-10-10T17:00:00Z");
    private final Event alongside = event("samtidigt", "2026-10-10T17:00:00Z");
    private final Event tomorrow = event("imorgon", "2026-10-11T10:00:00Z");

    @Test
    void anEventAlongsideTheLeadOneStays() {
        assertThat(ContentPageController.below(List.of(morning, evening, alongside, tomorrow), evening))
                .containsExactly(alongside, tomorrow);
    }

    @Test
    void withoutALeadEventEveryEventIsListed() {
        assertThat(ContentPageController.below(List.of(morning, tomorrow), null)).containsExactly(morning, tomorrow);
    }

    private static Event event(String slug, String startsAt) {
        return new Event(slug, slug, slug, null, Instant.parse(startsAt), null, null, null, "", List.of(), null);
    }
}
