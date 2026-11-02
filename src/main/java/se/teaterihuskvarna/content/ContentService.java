package se.teaterihuskvarna.content;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;

/// The public content from Sanity: events, news, the fixed pages with their
/// productions, board and Ludde prizes, and partners, R001 to R004.
///
/// Both adapters call these methods, the public JTE pages and `/api/content`,
/// per `docs/decisions/0014-one-service-layer-two-adapters.md`. Every method
/// takes a [Perspective], which is [Perspective#PUBLISHED] except for an editor
/// the Studio sent to the preview.
///
/// A document an editor left incomplete, such as an event without a date, is
/// skipped rather than shown half.
@Service
public class ContentService {

    /// The fixed pages R004 names, each at `/<slug>` and showing the `sida`
    /// document with that slug. Partners are a type of their own.
    public static final List<String> PAGES = List.of(
            "om-foreningen", "styrelsen", "produktioner", "ludde-priser", "kontakt");

    private static final ZoneId SWEDEN = ZoneId.of("Europe/Stockholm");

    private final ContentCache cache;
    private final PortableText portableText;
    private final Images images;
    private final Clock clock;
    private final ContentSettings settings;

    ContentService(ContentCache cache, Images images, Clock clock, ContentSettings settings) {
        this.cache = cache;
        this.settings = settings;
        this.images = images;
        this.portableText = new PortableText(images);
        this.clock = clock;
    }

    /// The event the start page leads with: R001. The first event still to
    /// start today leads. When none is left today, the one that started last
    /// today stays on top until midnight, so an evening performance leads
    /// while it runs. Otherwise the first event after today leads.
    ///
    /// @param perspective published, or drafts for a preview
    /// @return the event to lead with, or empty when none is planned
    public Optional<Event> nextEvent(Perspective perspective) {
        return lead(upcomingEvents(null, perspective), clock.instant());
    }

    /// @param upcoming events from the start of today in Sweden, earliest first
    /// @param now      the moment
    /// @return the event [#nextEvent] leads with
    static Optional<Event> lead(List<Event> upcoming, Instant now) {
        LocalDate today = LocalDate.ofInstant(now, SWEDEN);
        Event startedToday = null;
        for (Event event : upcoming) {
            if (event.startsAt().isAfter(now)) {
                boolean laterToday = LocalDate.ofInstant(event.startsAt(), SWEDEN).equals(today);
                return Optional.of(laterToday || startedToday == null ? event : startedToday);
            }
            startedToday = event;
        }
        return Optional.ofNullable(startedToday);
    }

    /// @param seriesSlug  only events in this series, or null for every event
    /// @param perspective published, or drafts for a preview
    /// @return events from the start of today in Sweden onwards, earliest first
    public List<Event> upcomingEvents(@Nullable String seriesSlug, Perspective perspective) {
        Instant today = LocalDate.now(clock.withZone(SWEDEN)).atStartOfDay(SWEDEN).toInstant();
        return events(perspective).stream()
                .filter(event -> !event.startsAt().isBefore(today))
                .filter(event -> seriesSlug == null || inSeries(event, seriesSlug))
                .toList();
    }

    /// @param slug        the event's slug
    /// @param perspective published, or drafts for a preview
    /// @return the event, past or upcoming, or empty if no event has that slug
    public Optional<Event> event(String slug, Perspective perspective) {
        return events(perspective).stream().filter(event -> event.slug().equals(slug)).findFirst();
    }

    /// @param id the published document id a volunteer shift stores
    /// @return the published event, past or upcoming, or empty if it is gone or unpublished
    public Optional<Event> eventById(String id) {
        return events(Perspective.PUBLISHED).stream().filter(event -> event.id().equals(id)).findFirst();
    }

    /// @param perspective published, or drafts for a preview
    /// @return every series, by name
    public List<Series> series(Perspective perspective) {
        List<Series> series = new ArrayList<>();
        for (JsonNode document : cache.documents("serie", perspective)) {
            Series one = series(document);
            if (one != null) {
                series.add(one);
            }
        }
        series.sort(Comparator.comparing(one -> one.title().toLowerCase(Locale.ROOT)));
        return series;
    }

    /// News with a publishing date in the future stays hidden until then, so an
    /// editor can publish ahead: R003.
    ///
    /// @param perspective published, or drafts for a preview, which also shows future items
    /// @return the news, newest first
    public List<NewsItem> news(Perspective perspective) {
        Instant now = clock.instant();
        List<NewsItem> news = new ArrayList<>();
        for (JsonNode document : cache.documents("nyhet", perspective)) {
            NewsItem item = newsItem(document);
            if (item != null && (perspective == Perspective.DRAFTS || !item.publishedAt().isAfter(now))) {
                news.add(item);
            }
        }
        news.sort(Comparator.comparing(NewsItem::publishedAt).reversed());
        return news;
    }

    /// @param slug        the news item's slug
    /// @param perspective published, or drafts for a preview
    /// @return the item, or empty if none has that slug or it is not yet due
    public Optional<NewsItem> newsItem(String slug, Perspective perspective) {
        return news(perspective).stream().filter(item -> item.slug().equals(slug)).findFirst();
    }

    /// @param slug        the `sida` document's slug, one of [#PAGES]
    /// @param perspective published, or drafts for a preview
    /// @return the page, or empty if the editors have not written it
    public Optional<Page> page(String slug, Perspective perspective) {
        for (JsonNode document : cache.documents("sida", perspective)) {
            String title = Json.text(document, "title");
            if (slug.equals(Json.slug(document)) && title != null) {
                return Optional.of(new Page(slug, title, images.of(document.get("headerbild")),
                        portableText.html(document.get("innehall"))));
            }
        }
        return Optional.empty();
    }

    /// @param perspective published, or drafts for a preview
    /// @return every production, the latest year first and those without a year last
    public List<Production> productions(Perspective perspective) {
        List<Production> productions = new ArrayList<>();
        for (JsonNode document : cache.documents("produktion", perspective)) {
            String title = Json.text(document, "title");
            if (title != null) {
                Integer first = Json.integer(document, "ar");
                Integer last = Json.integer(document, "tillAr");
                productions.add(new Production(title, first, first == null || first.equals(last) ? null : last,
                        images.of(document.get("huvudbild")), portableText.html(document.get("beskrivning"))));
            }
        }
        productions.sort(Comparator.comparing(ContentService::latestYear,
                Comparator.nullsLast(Comparator.<Integer>reverseOrder()))
                .thenComparing(production -> production.title().toLowerCase(Locale.ROOT)));
        return productions;
    }

    /// @param perspective published, or drafts for a preview
    /// @return the board, in the order the editors gave, then by name
    public List<BoardMember> board(Perspective perspective) {
        record Ordered(@Nullable Integer order, BoardMember member) {
        }
        List<Ordered> board = new ArrayList<>();
        for (JsonNode document : cache.documents("styrelseledamot", perspective)) {
            String name = Json.text(document, "namn");
            String role = Json.text(document, "roll");
            if (name != null && role != null) {
                board.add(new Ordered(Json.integer(document, "ordning"), new BoardMember(name, role,
                        images.of(document.get("bild")), Json.text(document, "ansvar"))));
            }
        }
        board.sort(Comparator.comparing(Ordered::order, Comparator.nullsLast(Comparator.<Integer>naturalOrder()))
                .thenComparing(ordered -> ordered.member().name().toLowerCase(Locale.ROOT)));
        return board.stream().map(Ordered::member).toList();
    }

    /// @param perspective published, or drafts for a preview
    /// @return every Ludde prize, the latest year first
    public List<LuddePrize> luddePrizes(Perspective perspective) {
        List<LuddePrize> prizes = new ArrayList<>();
        for (JsonNode document : cache.documents("luddepris", perspective)) {
            String recipient = Json.text(document, "mottagare");
            Integer year = Json.integer(document, "ar");
            if (recipient != null && year != null) {
                prizes.add(new LuddePrize(recipient, year, images.of(document.get("bild")),
                        portableText.html(document.get("motivering"))));
            }
        }
        prizes.sort(Comparator.comparing(LuddePrize::year).reversed()
                .thenComparing(prize -> prize.recipient().toLowerCase(Locale.ROOT)));
        return prizes;
    }

    /// @param perspective published, or drafts for a preview
    /// @return every partner, by name
    public List<Partner> partners(Perspective perspective) {
        List<Partner> partners = new ArrayList<>();
        for (JsonNode document : cache.documents("partner", perspective)) {
            String name = Json.text(document, "namn");
            if (name != null) {
                partners.add(new Partner(name, images.of(document.get("logotyp")),
                        webAddress(Json.text(document, "webbplats")), Json.text(document, "beskrivning")));
            }
        }
        partners.sort(Comparator.comparing(partner -> partner.name().toLowerCase(Locale.ROOT)));
        return partners;
    }

    /// Sanity's webhook, called when a document is published, changed or
    /// unpublished. Every copy expires, so the next visitor sees the change:
    /// R008. What changed does not matter; there are few enough documents to
    /// fetch them all again.
    ///
    /// @param signature the `sanity-webhook-signature` header, or null when it is missing
    /// @param body      the request body exactly as sent, which the signature covers
    /// @throws InvalidSignature if the signature does not match, or no secret is configured
    public void published(@Nullable String signature, byte[] body) {
        if (!WebhookSignature.valid(settings.webhookSecret(), signature, body)) {
            throw new InvalidSignature();
        }
        cache.expireAll();
    }

    private List<Event> events(Perspective perspective) {
        List<Event> events = new ArrayList<>();
        for (JsonNode document : cache.documents("evenemang", perspective)) {
            Event event = event(document);
            if (event != null) {
                events.add(event);
            }
        }
        events.sort(Comparator.comparing(Event::startsAt));
        return events;
    }

    private @Nullable Event event(JsonNode document) {
        String id = Json.text(document, "_id");
        String slug = Json.slug(document);
        String title = Json.text(document, "title");
        Instant startsAt = Json.instant(document, "datumTid");
        if (id == null || slug == null || title == null || startsAt == null) {
            return null;
        }
        List<Image> gallery = new ArrayList<>();
        for (JsonNode picture : document.path("bildgalleri")) {
            Image image = images.of(picture);
            if (image != null) {
                gallery.add(image);
            }
        }
        return new Event(publishedId(id), slug, title, series(document.get("serie")), startsAt,
                Json.text(document, "plats"), Json.text(document, "ingress"), images.of(document.get("huvudbild")),
                portableText.html(document.get("beskrivning")), List.copyOf(gallery),
                webAddress(Json.text(document, "biljettlank")));
    }

    private @Nullable NewsItem newsItem(JsonNode document) {
        String slug = Json.slug(document);
        String title = Json.text(document, "title");
        Instant publishedAt = Json.instant(document, "publiceringsdatum");
        if (publishedAt == null) {
            publishedAt = Json.instant(document, "_createdAt");
        }
        if (slug == null || title == null || publishedAt == null) {
            return null;
        }
        return new NewsItem(slug, title, publishedAt, Json.text(document, "ingress"),
                images.of(document.get("huvudbild")), portableText.html(document.get("brodtext")),
                webAddress(Json.text(document, "video")));
    }

    private static boolean inSeries(Event event, String seriesSlug) {
        Series series = event.series();
        return series != null && series.slug().equals(seriesSlug);
    }

    private static @Nullable Series series(@Nullable JsonNode node) {
        if (node == null) {
            return null;
        }
        String slug = Json.slug(node);
        String title = Json.text(node, "title");
        return slug == null || title == null ? null : new Series(slug, title);
    }

    /// Drafts come back with their published id under the drafts perspective,
    /// but a fixture or an older API version may give `drafts.<id>`.
    private static String publishedId(String id) {
        return id.startsWith("drafts.") ? id.substring("drafts.".length()) : id;
    }

    /// @return the address if it is http or https, which is all a link on the page may be, otherwise null
    private static @Nullable String webAddress(@Nullable String url) {
        if (url == null) {
            return null;
        }
        String lower = url.strip().toLowerCase(Locale.ROOT);
        return lower.startsWith("https://") || lower.startsWith("http://") ? url.strip() : null;
    }

    private static @Nullable Integer latestYear(Production production) {
        Integer last = production.lastYear();
        return last != null ? last : production.firstYear();
    }
}
