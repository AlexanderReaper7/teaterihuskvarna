package se.teaterihuskvarna.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.StringNode;

/// R008's minute, and what a visitor sees while Sanity is down.
class ContentCacheTest {

    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");

    private final CountingSource source = new CountingSource();
    private final MovableClock clock = new MovableClock();
    private final ContentCache cache = new ContentCache(source, Duration.ofSeconds(45), clock);

    @Test
    void aCopyIsKeptForMaxAgeThenFetchedAgain() {
        cache.documents("nyhet", Perspective.PUBLISHED);
        clock.now = NOW.plusSeconds(44);
        cache.documents("nyhet", Perspective.PUBLISHED);
        assertThat(source.fetches).isEqualTo(1);

        clock.now = NOW.plusSeconds(45);
        cache.documents("nyhet", Perspective.PUBLISHED);
        assertThat(source.fetches).isEqualTo(2);
    }

    @Test
    void theWebhookExpiresEveryCopy() {
        cache.documents("nyhet", Perspective.PUBLISHED);
        cache.expireAll();
        cache.documents("nyhet", Perspective.PUBLISHED);

        assertThat(source.fetches).isEqualTo(2);
    }

    @Test
    void draftsAreNeverKept() {
        cache.documents("nyhet", Perspective.DRAFTS);
        cache.documents("nyhet", Perspective.DRAFTS);

        assertThat(source.fetches).isEqualTo(2);
    }

    @Test
    void aFailureShowsTheLastCopy() {
        List<JsonNode> first = cache.documents("nyhet", Perspective.PUBLISHED);
        source.failing = true;
        clock.now = NOW.plusSeconds(60);

        assertThat(cache.documents("nyhet", Perspective.PUBLISHED)).isEqualTo(first);
    }

    @Test
    void aFailureWithNoCopyReachesTheVisitor() {
        source.failing = true;

        assertThatThrownBy(() -> cache.documents("nyhet", Perspective.PUBLISHED))
                .isInstanceOf(ContentUnavailable.class);
    }

    private static final class CountingSource implements ContentSource {

        private int fetches;
        private boolean failing;

        @Override
        public List<JsonNode> documents(String type, Perspective perspective) {
            if (failing) {
                throw new ContentUnavailable("down");
            }
            fetches++;
            return List.of(StringNode.valueOf(type + fetches));
        }

        @Override
        public boolean previewSecretValid(String secret) {
            return false;
        }
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
