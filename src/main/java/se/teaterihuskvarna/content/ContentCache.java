package se.teaterihuskvarna.content;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

/// The published documents of each type, kept for at most `maxAge` and
/// fetched again after that, or as soon as Sanity's webhook says something
/// was published: `docs/projektplan.md`, "Sanity content is cached".
///
/// One request at a time asks Sanity for a type. While it waits, and for
/// `retryAfter` after a fetch that failed, every other request gets the last
/// copy at once, since a page a few minutes old is better than an error page.
/// A Sanity that hangs therefore holds one request thread per type, not
/// every thread the server has.
///
/// With no copy at all, the requests that arrive during the first fetch wait
/// for it together and share its answer. Once a fetch has failed, the
/// failure goes to the visitor as [ContentUnavailable] at once, and one
/// request per `retryAfter` asks Sanity again.
///
/// Drafts are never kept: a preview has to show the edit just made.
final class ContentCache {

    private static final Logger LOG = LoggerFactory.getLogger(ContentCache.class);

    private final ContentSource source;
    private final Duration maxAge;
    private final Duration retryAfter;
    private final Clock clock;
    private final Map<String, Copy> copies = new ConcurrentHashMap<>();

    /// @param source     where the documents come from
    /// @param maxAge     how long a fetched list counts as fresh
    /// @param retryAfter how long the next fetch waits after one that failed
    /// @param clock      the time
    ContentCache(ContentSource source, Duration maxAge, Duration retryAfter, Clock clock) {
        this.source = source;
        this.maxAge = maxAge;
        this.retryAfter = retryAfter;
        this.clock = clock;
    }

    /// @param type        a document type
    /// @param perspective published for visitors, drafts for a preview
    /// @return the documents, no older than `maxAge` unless Sanity is failing or another request is fetching them
    /// @throws ContentUnavailable if no earlier copy is kept and Sanity fails, or failed within `retryAfter`
    List<JsonNode> documents(String type, Perspective perspective) {
        if (perspective == Perspective.DRAFTS) {
            return source.documents(type, perspective);
        }
        return copies.computeIfAbsent(type, Copy::new).documents();
    }

    /// Makes every copy stale and ends every back-off, so the next request
    /// fetches again. The copies stay, for a Sanity that fails right after the
    /// webhook.
    void expireAll() {
        copies.values().forEach(Copy::expire);
    }

    /// One type's documents and the state of fetching them. The monitor guards
    /// the fields and is never held while Sanity is asked.
    private final class Copy {

        private final String type;
        private @Nullable List<JsonNode> documents;
        /// The documents are fresh before this moment.
        private Instant expires = Instant.MIN;
        /// After a failure, no request asks Sanity before this moment.
        private Instant retryAt = Instant.MIN;
        /// Why the last fetch failed, or null when it succeeded or none has ended.
        private @Nullable ContentUnavailable failure;
        /// The fetch one request is making now, or null.
        private @Nullable CompletableFuture<List<JsonNode>> fetch;
        /// How many webhooks have arrived, so a fetch can tell that one arrived while it ran.
        private long webhooks;

        Copy(String type) {
            this.type = type;
        }

        List<JsonNode> documents() {
            CompletableFuture<List<JsonNode>> mine = new CompletableFuture<>();
            CompletableFuture<List<JsonNode>> first;
            long webhooksBefore;
            synchronized (this) {
                List<JsonNode> last = documents;
                Instant now = clock.instant();
                if (last != null && now.isBefore(expires)) {
                    return last;
                }
                if (fetch != null || now.isBefore(retryAt)) {
                    if (last != null) {
                        return last;
                    }
                    ContentUnavailable failed = failure;
                    if (failed != null) {
                        throw new ContentUnavailable("No copy of " + type + " is kept, and Sanity failed: "
                                + failed.getMessage(), failed);
                    }
                }
                // Not null only with no copy and no failure behind: the first fetch is running.
                first = fetch;
                if (first == null) {
                    fetch = mine;
                }
                webhooksBefore = webhooks;
            }
            return first == null ? fetch(mine, webhooksBefore) : shared(first);
        }

        synchronized void expire() {
            expires = Instant.MIN;
            retryAt = Instant.MIN;
            webhooks++;
        }

        /// Asks Sanity, as the only request doing so for this type.
        private List<JsonNode> fetch(CompletableFuture<List<JsonNode>> mine, long webhooksBefore) {
            try {
                List<JsonNode> fetched = List.copyOf(source.documents(type, Perspective.PUBLISHED));
                stored(fetched, webhooksBefore);
                mine.complete(fetched);
                return fetched;
            } catch (ContentUnavailable e) {
                List<JsonNode> last = failed(e, webhooksBefore);
                mine.completeExceptionally(e);
                if (last == null) {
                    throw e;
                }
                LOG.warn("Showing the last copy of {}: {}", type, e.getMessage());
                return last;
            } finally {
                // Reached undone only when the source threw something else. The
                // next request then asks again, and nobody waits for this one.
                if (!mine.isDone()) {
                    abandoned();
                    mine.completeExceptionally(new IllegalStateException("The fetch of " + type + " broke off"));
                }
            }
        }

        /// A webhook that arrived during the fetch may be for a publish the
        /// fetch did not see, so then the documents are stored as stale.
        private synchronized void stored(List<JsonNode> fetched, long webhooksBefore) {
            documents = fetched;
            expires = webhooks == webhooksBefore ? clock.instant().plus(maxAge) : Instant.MIN;
            failure = null;
            fetch = null;
        }

        /// A webhook that arrived during the fetch asked for a new attempt, so
        /// then no back-off starts.
        ///
        /// @return the last copy, or null when there is none
        private synchronized @Nullable List<JsonNode> failed(ContentUnavailable e, long webhooksBefore) {
            retryAt = webhooks == webhooksBefore ? clock.instant().plus(retryAfter) : Instant.MIN;
            failure = e;
            fetch = null;
            return documents;
        }

        private synchronized void abandoned() {
            fetch = null;
        }

        /// Waits for the first fetch, which another request is making.
        private List<JsonNode> shared(CompletableFuture<List<JsonNode>> first) {
            try {
                return first.join();
            } catch (CompletionException e) {
                Throwable cause = e.getCause() == null ? e : e.getCause();
                throw new ContentUnavailable("No copy of " + type + " is kept, and the first fetch failed: "
                        + cause.getMessage(), cause);
            }
        }
    }
}
