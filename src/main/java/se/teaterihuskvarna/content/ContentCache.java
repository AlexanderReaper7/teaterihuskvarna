package se.teaterihuskvarna.content;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.JsonNode;

/// The published documents of each type, kept for at most `maxAge` and
/// fetched again after that, or as soon as Sanity's webhook says something
/// was published: `docs/projektplan.md`, "Sanity content is cached".
///
/// When a fetch fails, the last copy is shown for as long as the failure lasts,
/// since a page a few minutes old is better than an error page. With no copy
/// at all, the failure goes to the visitor as [ContentUnavailable].
///
/// Drafts are never kept: a preview has to show the edit just made.
final class ContentCache {

    private static final Logger LOG = LoggerFactory.getLogger(ContentCache.class);

    private final ContentSource source;
    private final Duration maxAge;
    private final Clock clock;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    ContentCache(ContentSource source, Duration maxAge, Clock clock) {
        this.source = source;
        this.maxAge = maxAge;
        this.clock = clock;
    }

    /// @param type        a document type
    /// @param perspective published for visitors, drafts for a preview
    /// @return the documents, no older than `maxAge` unless Sanity is failing
    /// @throws ContentUnavailable if Sanity fails and no earlier copy is kept
    List<JsonNode> documents(String type, Perspective perspective) {
        if (perspective == Perspective.DRAFTS) {
            return source.documents(type, perspective);
        }
        Entry entry = entries.get(type);
        Instant now = clock.instant();
        if (entry != null && entry.fresh(now)) {
            return entry.documents();
        }
        // One fetch per type at a time, so an expired entry on a busy page
        // costs one query rather than one per visitor.
        synchronized (lock(type)) {
            entry = entries.get(type);
            if (entry != null && entry.fresh(clock.instant())) {
                return entry.documents();
            }
            try {
                List<JsonNode> documents = List.copyOf(source.documents(type, perspective));
                entries.put(type, new Entry(documents, clock.instant().plus(maxAge)));
                return documents;
            } catch (ContentUnavailable e) {
                if (entry == null) {
                    throw e;
                }
                LOG.warn("Showing the last copy of {}: {}", type, e.getMessage());
                return entry.documents();
            }
        }
    }

    /// Makes every entry stale, so the next request fetches again. The copies
    /// stay, for a Sanity that fails right after the webhook.
    void expireAll() {
        entries.replaceAll((type, entry) -> new Entry(entry.documents(), Instant.MIN));
    }

    private final Map<String, Object> locks = new ConcurrentHashMap<>();

    private Object lock(String type) {
        return locks.computeIfAbsent(type, key -> new Object());
    }

    private record Entry(List<JsonNode> documents, Instant expires) {

        boolean fresh(Instant now) {
            return now.isBefore(expires);
        }
    }
}
