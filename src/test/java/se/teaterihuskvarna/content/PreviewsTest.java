package se.teaterihuskvarna.content;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/// A pass shows drafts for its hour and no longer, and only a pass the same
/// instance signed counts.
class PreviewsTest {

    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");

    private final MovableClock clock = new MovableClock();

    @Test
    void aValidSecretGivesAPassThatShowsDraftsUntilItExpires() {
        Previews previews = new Previews(source(true), clock);
        String pass = previews.start("hemlig").orElseThrow().value();

        clock.now = NOW.plus(Previews.LIFETIME).minusSeconds(1);
        assertThat(previews.perspective(pass)).isEqualTo(Perspective.DRAFTS);
        clock.now = NOW.plus(Previews.LIFETIME);
        assertThat(previews.perspective(pass)).isEqualTo(Perspective.PUBLISHED);
    }

    @Test
    void aWrongOrBlankSecretGivesNoPass() {
        assertThat(new Previews(source(false), clock).start("hemlig")).isEmpty();
        assertThat(new Previews(source(true), clock).start(" ")).isEmpty();
    }

    @Test
    void anotherInstancesPassDoesNotCount() {
        String pass = new Previews(source(true), clock).start("hemlig").orElseThrow().value();

        assertThat(new Previews(source(true), clock).perspective(pass)).isEqualTo(Perspective.PUBLISHED);
    }

    @Test
    void aChangedExpiryDoesNotCount() {
        Previews previews = new Previews(source(true), clock);
        String pass = previews.start("hemlig").orElseThrow().value();
        String later = "9" + pass;

        assertThat(previews.perspective(later)).isEqualTo(Perspective.PUBLISHED);
    }

    @Test
    void anythingElseIsPublished() {
        Previews previews = new Previews(source(true), clock);
        for (String pass : Arrays.asList(null, "", ".", "abc", "1.%%%", "x.AAAA")) {
            assertThat(previews.perspective(pass)).as(pass).isEqualTo(Perspective.PUBLISHED);
        }
    }

    private static ContentSource source(boolean valid) {
        return new ContentSource() {
            @Override
            public List<JsonNode> documents(String type, Perspective perspective) {
                return List.of();
            }

            @Override
            public boolean previewSecretValid(String secret) {
                return valid;
            }
        };
    }

    private static final class MovableClock extends Clock {

        private Instant now = NOW;

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
