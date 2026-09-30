package se.teaterihuskvarna.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
        String pass = previews.start("hemlig", "192.0.2.1").orElseThrow().value();

        clock.now = NOW.plus(Previews.LIFETIME).minusSeconds(1);
        assertThat(previews.perspective(pass)).isEqualTo(Perspective.DRAFTS);
        clock.now = NOW.plus(Previews.LIFETIME);
        assertThat(previews.perspective(pass)).isEqualTo(Perspective.PUBLISHED);
    }

    @Test
    void aWrongOrBlankSecretGivesNoPass() {
        assertThat(new Previews(source(false), clock).start("hemlig", "192.0.2.1")).isEmpty();
        assertThat(new Previews(source(true), clock).start(" ", "192.0.2.1")).isEmpty();
    }

    @Test
    void anotherInstancesPassDoesNotCount() {
        String pass = new Previews(source(true), clock).start("hemlig", "192.0.2.1").orElseThrow().value();

        assertThat(new Previews(source(true), clock).perspective(pass)).isEqualTo(Perspective.PUBLISHED);
    }

    @Test
    void aChangedExpiryDoesNotCount() {
        Previews previews = new Previews(source(true), clock);
        String pass = previews.start("hemlig", "192.0.2.1").orElseThrow().value();
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

    @Test
    void invalidSecretsCountAndAnotherClientCanStillPreview() {
        ContentSource source = mock(ContentSource.class);
        when(source.previewSecretValid("valid")).thenReturn(true);
        Previews previews = new Previews(source, clock);

        for (int attempt = 0; attempt < 20; attempt++) {
            assertThat(previews.start("wrong-" + attempt, "192.0.2.1")).isEmpty();
        }
        assertThatThrownBy(() -> previews.start("valid", "192.0.2.1")).isInstanceOf(TooManyPreviews.class);
        verify(source, times(20)).previewSecretValid(anyString());
        String pass = previews.start("valid", "192.0.2.2").orElseThrow().value();
        assertThat(previews.perspective(pass)).isEqualTo(Perspective.DRAFTS);
    }

    @Test
    void theRollingMinuteRestoresOnlyExpiredAttempts() {
        ContentSource source = mock(ContentSource.class);
        Previews previews = new Previews(source, clock);
        previews.start("wrong", "192.0.2.1");
        clock.now = NOW.plusSeconds(30);
        for (int attempt = 0; attempt < 19; attempt++) {
            previews.start("wrong", "192.0.2.1");
        }

        clock.now = NOW.plusSeconds(59);
        assertThatThrownBy(() -> previews.start("wrong", "192.0.2.1")).isInstanceOf(TooManyPreviews.class);
        clock.now = NOW.plusSeconds(60);
        assertThat(previews.start("wrong", "192.0.2.1")).isEmpty();
        assertThatThrownBy(() -> previews.start("wrong", "192.0.2.1")).isInstanceOf(TooManyPreviews.class);
        verify(source, times(21)).previewSecretValid("wrong");
    }

    @Test
    void failedSanityQueriesUseTheAllowanceToo() {
        ContentSource source = mock(ContentSource.class);
        when(source.previewSecretValid(anyString())).thenThrow(new ContentUnavailable("offline"));
        Previews previews = new Previews(source, clock);

        for (int attempt = 0; attempt < 20; attempt++) {
            assertThatThrownBy(() -> previews.start("secret", "192.0.2.1"))
                    .isInstanceOf(ContentUnavailable.class);
        }
        assertThatThrownBy(() -> previews.start("secret", "192.0.2.1")).isInstanceOf(TooManyPreviews.class);
        verify(source, times(20)).previewSecretValid("secret");
    }

    @Test
    void concurrentExchangesCannotExceedTheAllowance() throws Exception {
        ContentSource source = mock(ContentSource.class);
        Previews previews = new Previews(source, clock);
        CountDownLatch ready = new CountDownLatch(40);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> attempts = new ArrayList<>();

        try (ExecutorService threads = Executors.newFixedThreadPool(40)) {
            for (int attempt = 0; attempt < 40; attempt++) {
                attempts.add(threads.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        previews.start("wrong", "192.0.2.1");
                        return true;
                    } catch (TooManyPreviews e) {
                        return false;
                    }
                }));
            }
            boolean allReady = ready.await(5, TimeUnit.SECONDS);
            start.countDown();
            assertThat(allReady).isTrue();
            int accepted = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get(5, TimeUnit.SECONDS)) {
                    accepted++;
                }
            }
            assertThat(accepted).isEqualTo(20);
        }
        verify(source, times(20)).previewSecretValid("wrong");
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
