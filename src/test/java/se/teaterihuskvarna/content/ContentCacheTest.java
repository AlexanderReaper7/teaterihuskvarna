package se.teaterihuskvarna.content;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.StringNode;

/// R008's minute, and what a visitor sees while Sanity is down or hangs.
class ContentCacheTest {

    private static final Instant NOW = Instant.parse("2026-09-28T12:00:00Z");

    /// How long "at once" may take. The cache answers in microseconds; a
    /// request that waits for a hanging source never answers within it.
    private static final Duration AT_ONCE = Duration.ofSeconds(5);

    private static final int OTHERS = 8;

    private final CountingSource source = new CountingSource();
    private final MovableClock clock = new MovableClock();
    private final ContentCache cache = new ContentCache(source, Duration.ofSeconds(45), Duration.ofSeconds(10), clock);
    private final ExecutorService requests = Executors.newCachedThreadPool();

    @AfterEach
    void letEveryRequestEnd() throws InterruptedException {
        source.release();
        requests.shutdown();
        assertThat(requests.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
    }

    @Test
    void aCopyIsKeptForMaxAgeThenFetchedAgain() {
        cache.documents("nyhet", Perspective.PUBLISHED);
        clock.now = NOW.plusSeconds(44);
        cache.documents("nyhet", Perspective.PUBLISHED);
        assertThat(source.fetches()).isEqualTo(1);

        clock.now = NOW.plusSeconds(45);
        cache.documents("nyhet", Perspective.PUBLISHED);
        assertThat(source.fetches()).isEqualTo(2);
    }

    @Test
    void theWebhookExpiresEveryCopy() {
        cache.documents("nyhet", Perspective.PUBLISHED);
        cache.expireAll();
        cache.documents("nyhet", Perspective.PUBLISHED);

        assertThat(source.fetches()).isEqualTo(2);
    }

    @Test
    void draftsAreNeverKept() {
        cache.documents("nyhet", Perspective.DRAFTS);
        cache.documents("nyhet", Perspective.DRAFTS);

        assertThat(source.fetches()).isEqualTo(2);
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

    /// A Sanity that accepts the connection and never answers holds one
    /// request, the one that fetches. The page of every other visitor shows
    /// the last copy without waiting for it.
    @Test
    void whileAFetchHangsEveryOtherRequestGetsTheLastCopyAtOnce() {
        List<JsonNode> first = cache.documents("nyhet", Perspective.PUBLISHED);
        clock.now = NOW.plusSeconds(60);
        source.failing = true;
        Future<List<JsonNode>> stuck = requestThatHangs();

        for (Future<List<JsonNode>> other : requests(OTHERS)) {
            assertThat(other).succeedsWithin(AT_ONCE).isEqualTo(first);
        }
        assertThat(stuck).isNotDone();
        assertThat(source.fetches()).as("the first fetch and the one that hangs").isEqualTo(2);

        source.release();
        assertThat(stuck).succeedsWithin(AT_ONCE).isEqualTo(first);
    }

    @Test
    void aFailureIsNotTriedAgainUntilTheBackOffEnds() {
        List<JsonNode> first = cache.documents("nyhet", Perspective.PUBLISHED);
        source.failing = true;
        clock.now = NOW.plusSeconds(60);
        assertThat(cache.documents("nyhet", Perspective.PUBLISHED)).isEqualTo(first);
        assertThat(source.fetches()).isEqualTo(2);

        clock.now = NOW.plusSeconds(69);
        assertThat(cache.documents("nyhet", Perspective.PUBLISHED)).isEqualTo(first);
        assertThat(source.fetches()).as("9 s after the failure").isEqualTo(2);

        source.failing = false;
        clock.now = NOW.plusSeconds(70);
        assertThat(cache.documents("nyhet", Perspective.PUBLISHED)).isNotEqualTo(first);
        assertThat(source.fetches()).as("10 s after the failure").isEqualTo(3);
    }

    /// A cold start with Sanity down: the answer is 503 either way, but only
    /// one request per back-off may spend a timeout finding that out.
    @Test
    void withNoCopyAFailureIsNotTriedAgainUntilTheBackOffEnds() {
        source.failing = true;
        assertThatThrownBy(() -> cache.documents("nyhet", Perspective.PUBLISHED))
                .isInstanceOf(ContentUnavailable.class);

        clock.now = NOW.plusSeconds(9);
        assertThatThrownBy(() -> cache.documents("nyhet", Perspective.PUBLISHED))
                .isInstanceOf(ContentUnavailable.class);
        assertThat(source.fetches()).as("9 s after the failure").isEqualTo(1);

        source.failing = false;
        clock.now = NOW.plusSeconds(10);
        assertThat(cache.documents("nyhet", Perspective.PUBLISHED)).isNotEmpty();
        assertThat(source.fetches()).as("10 s after the failure").isEqualTo(2);
    }

    /// The requests that arrive during the very first fetch wait for it
    /// together and fail with it, instead of each asking a hanging Sanity in
    /// turn.
    @Test
    void withNoCopyTheRequestsDuringTheFirstFetchShareItsFailure() {
        source.failing = true;
        Future<List<JsonNode>> stuck = requestThatHangs();
        List<Future<List<JsonNode>>> others = requests(OTHERS);

        source.release();

        assertThat(stuck).failsWithin(AT_ONCE).withThrowableOfType(ExecutionException.class)
                .withCauseInstanceOf(ContentUnavailable.class);
        for (Future<List<JsonNode>> other : others) {
            assertThat(other).failsWithin(AT_ONCE).withThrowableOfType(ExecutionException.class)
                    .withCauseInstanceOf(ContentUnavailable.class);
        }
        assertThat(source.fetches()).isEqualTo(1);
    }

    /// The same first fetch against a Sanity that is only slow: everyone gets
    /// the page, from one query.
    @Test
    void withNoCopyTheRequestsDuringTheFirstFetchShareItsDocuments() {
        Future<List<JsonNode>> slow = requestThatHangs();
        List<Future<List<JsonNode>>> others = requests(OTHERS);

        source.release();

        assertThat(slow).succeedsWithin(AT_ONCE);
        for (Future<List<JsonNode>> other : others) {
            assertThat(other).succeedsWithin(AT_ONCE).isEqualTo(slow.resultNow());
        }
        assertThat(source.fetches()).isEqualTo(1);
    }

    /// Once a fetch has failed with no copy, the next attempt holds only the
    /// request that makes it.
    @Test
    void withNoCopyAndAFailureBehindOnlyOneRequestWaitsForTheNextAttempt() {
        source.failing = true;
        assertThatThrownBy(() -> cache.documents("nyhet", Perspective.PUBLISHED))
                .isInstanceOf(ContentUnavailable.class);
        clock.now = NOW.plusSeconds(10);
        Future<List<JsonNode>> stuck = requestThatHangs();

        for (Future<List<JsonNode>> other : requests(OTHERS)) {
            assertThat(other).failsWithin(AT_ONCE).withThrowableOfType(ExecutionException.class)
                    .withCauseInstanceOf(ContentUnavailable.class);
        }
        assertThat(stuck).isNotDone();
        assertThat(source.fetches()).as("the failure and the attempt that hangs").isEqualTo(2);
    }

    /// R008: a publish must not wait out a back-off that an earlier failure
    /// started.
    @Test
    void aWebhookDuringTheBackOffFetchesAgain() {
        List<JsonNode> first = cache.documents("nyhet", Perspective.PUBLISHED);
        source.failing = true;
        clock.now = NOW.plusSeconds(60);
        assertThat(cache.documents("nyhet", Perspective.PUBLISHED)).isEqualTo(first);

        source.failing = false;
        cache.expireAll();

        assertThat(cache.documents("nyhet", Perspective.PUBLISHED)).isNotEqualTo(first);
        assertThat(source.fetches()).isEqualTo(3);
    }

    @Test
    void aWebhookDuringTheBackOffWithNoCopyFetchesAgain() {
        source.failing = true;
        assertThatThrownBy(() -> cache.documents("nyhet", Perspective.PUBLISHED))
                .isInstanceOf(ContentUnavailable.class);

        source.failing = false;
        cache.expireAll();

        assertThat(cache.documents("nyhet", Perspective.PUBLISHED)).isNotEmpty();
    }

    /// The fetch may have read Sanity before the publish, so what it stores
    /// does not count as fresh.
    @Test
    void aWebhookDuringAFetchMakesTheNextRequestFetchAgain() {
        cache.documents("nyhet", Perspective.PUBLISHED);
        clock.now = NOW.plusSeconds(60);
        Future<List<JsonNode>> running = requestThatHangs();

        cache.expireAll();
        source.release();

        assertThat(running).succeedsWithin(AT_ONCE);
        assertThat(cache.documents("nyhet", Perspective.PUBLISHED)).isNotEqualTo(running.resultNow());
        assertThat(source.fetches()).isEqualTo(3);
    }

    /// The webhook asked for a new attempt, so the failure of a fetch that
    /// began before it starts no back-off.
    @Test
    void aWebhookDuringAFetchThatFailsStartsNoBackOff() {
        List<JsonNode> first = cache.documents("nyhet", Perspective.PUBLISHED);
        clock.now = NOW.plusSeconds(60);
        source.failing = true;
        Future<List<JsonNode>> running = requestThatHangs();

        cache.expireAll();
        source.release();
        assertThat(running).succeedsWithin(AT_ONCE).isEqualTo(first);

        source.failing = false;
        assertThat(cache.documents("nyhet", Perspective.PUBLISHED)).isNotEqualTo(first);
    }

    /// A source that throws anything but [ContentUnavailable] has a bug, and
    /// the bug must not leave the type marked as being fetched for ever.
    @Test
    void aFetchThatBreaksOffDoesNotStopTheNextOne() {
        source.broken = true;
        assertThatThrownBy(() -> cache.documents("nyhet", Perspective.PUBLISHED))
                .isInstanceOf(IllegalStateException.class);

        source.broken = false;
        assertThat(cache.documents("nyhet", Perspective.PUBLISHED)).isNotEmpty();
    }

    /// @return a request for the news that has reached the source and waits there until [CountingSource#release]
    private Future<List<JsonNode>> requestThatHangs() {
        CountDownLatch reached = source.hang();
        Future<List<JsonNode>> request = requests.submit(() -> cache.documents("nyhet", Perspective.PUBLISHED));
        try {
            assertThat(reached.await(30, TimeUnit.SECONDS)).as("the request reached the source").isTrue();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
        return request;
    }

    /// @return that many requests for the news, each on a thread of its own
    private List<Future<List<JsonNode>>> requests(int count) {
        List<Future<List<JsonNode>>> all = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            all.add(requests.submit(() -> cache.documents("nyhet", Perspective.PUBLISHED)));
        }
        return all;
    }

    /// Counts every query, the failed ones too, and can hang like a Sanity
    /// that never answers.
    private static final class CountingSource implements ContentSource {

        private final AtomicInteger fetches = new AtomicInteger();
        private volatile boolean failing;
        private volatile boolean broken;
        private volatile @Nullable CountDownLatch reached;
        private volatile CountDownLatch released = new CountDownLatch(0);

        int fetches() {
            return fetches.get();
        }

        /// From now on a query waits for [#release] before it answers or fails.
        ///
        /// @return counts down when the first query has arrived
        CountDownLatch hang() {
            CountDownLatch arrival = new CountDownLatch(1);
            released = new CountDownLatch(1);
            reached = arrival;
            return arrival;
        }

        /// Ends the hang: with `failing` set this is the timeout, otherwise the late answer.
        void release() {
            released.countDown();
        }

        @Override
        public List<JsonNode> documents(String type, Perspective perspective) {
            int fetch = fetches.incrementAndGet();
            CountDownLatch arrival = reached;
            if (arrival != null) {
                arrival.countDown();
            }
            try {
                if (!released.await(60, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("the test never released the source");
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
            if (broken) {
                throw new IllegalStateException("a bug in the source");
            }
            if (failing) {
                throw new ContentUnavailable("down");
            }
            return List.of(StringNode.valueOf(type + fetch));
        }

        @Override
        public boolean previewSecretValid(String secret) {
            return false;
        }
    }

    private static final class MovableClock extends Clock {

        private volatile Instant now = NOW;

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
