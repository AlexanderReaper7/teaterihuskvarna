package se.teaterihuskvarna.api;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import se.teaterihuskvarna.content.ContentService;
import se.teaterihuskvarna.content.Event;
import se.teaterihuskvarna.content.NewsItem;
import se.teaterihuskvarna.content.Page;
import se.teaterihuskvarna.content.Partner;
import se.teaterihuskvarna.content.PreviewPass;
import se.teaterihuskvarna.content.Previews;
import se.teaterihuskvarna.content.Series;

/// The public content, as the public pages show it, and Sanity's webhook.
///
/// Published documents, unless the request carries a valid pass in the
/// `Preview-Pass` header, which [#preview] hands out for the secret the
/// Studio's Presentation tool writes (R009). With a pass, drafts show as
/// they do in the pages' preview.
@RestController
public class ContentController {

    /// The header a pass from [#preview] goes in.
    static final String PASS = "Preview-Pass";

    private final ContentService content;
    private final Previews previews;

    ContentController(ContentService content, Previews previews) {
        this.content = content;
        this.previews = previews;
    }

    /// R009: swaps the Studio's secret for a pass that shows drafts for an hour.
    ///
    /// @param request the `sanity-preview-secret` the Studio wrote to the dataset
    /// @param servletRequest supplies the client IP under the application's proxy configuration
    /// @return 200 with the pass, 403 for an unknown secret, or 429 after 20 exchanges per IP per minute
    @PostMapping("/api/content/preview")
    public ResponseEntity<PreviewPass> preview(@RequestBody PreviewRequest request, HttpServletRequest servletRequest) {
        String secret = request.secret();
        return previews.start(secret == null ? "" : secret, servletRequest.getRemoteAddr())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.FORBIDDEN).build());
    }

    /// @param pass a pass from [#preview], or none
    /// @return 200 with the first upcoming event, or 404 when none is planned
    @GetMapping("/api/content/next-event")
    public ResponseEntity<Event> nextEvent(@RequestHeader(name = PASS, required = false) @Nullable String pass) {
        return ResponseEntity.of(content.nextEvent(previews.perspective(pass)));
    }

    /// @param serie a series' slug, or none for every event
    /// @param pass a pass from [#preview], or none
    /// @return upcoming events, earliest first
    @GetMapping("/api/content/events")
    public List<Event> events(@RequestParam(name = "serie", required = false) @Nullable String serie,
            @RequestHeader(name = PASS, required = false) @Nullable String pass) {
        return content.upcomingEvents(serie == null || serie.isBlank() ? null : serie, previews.perspective(pass));
    }

    /// @param slug the event's slug
    /// @param pass a pass from [#preview], or none
    /// @return 200 with the event, or 404
    @GetMapping("/api/content/events/{slug}")
    public ResponseEntity<Event> event(@PathVariable String slug,
            @RequestHeader(name = PASS, required = false) @Nullable String pass) {
        return ResponseEntity.of(content.event(slug, previews.perspective(pass)));
    }

    /// @param pass a pass from [#preview], or none
    /// @return every series, by name
    @GetMapping("/api/content/series")
    public List<Series> series(@RequestHeader(name = PASS, required = false) @Nullable String pass) {
        return content.series(previews.perspective(pass));
    }

    /// @param pass a pass from [#preview], or none
    /// @return the news, newest first
    @GetMapping("/api/content/news")
    public List<NewsItem> news(@RequestHeader(name = PASS, required = false) @Nullable String pass) {
        return content.news(previews.perspective(pass));
    }

    /// @param slug the item's slug
    /// @param pass a pass from [#preview], or none
    /// @return 200 with the item, or 404
    @GetMapping("/api/content/news/{slug}")
    public ResponseEntity<NewsItem> newsItem(@PathVariable String slug,
            @RequestHeader(name = PASS, required = false) @Nullable String pass) {
        return ResponseEntity.of(content.newsItem(slug, previews.perspective(pass)));
    }

    /// @param slug one of [ContentService#PAGES]
    /// @param pass a pass from [#preview], or none
    /// @return 200 with the page, or 404
    @GetMapping("/api/content/pages/{slug}")
    public ResponseEntity<Page> page(@PathVariable String slug,
            @RequestHeader(name = PASS, required = false) @Nullable String pass) {
        return ContentService.PAGES.contains(slug)
                ? ResponseEntity.of(content.page(slug, previews.perspective(pass)))
                : ResponseEntity.notFound().build();
    }

    /// @param pass a pass from [#preview], or none
    /// @return every partner, by name
    @GetMapping("/api/content/partners")
    public List<Partner> partners(@RequestHeader(name = PASS, required = false) @Nullable String pass) {
        return content.partners(previews.perspective(pass));
    }

    /// Sanity calls this when a document is published, changed or unpublished,
    /// with a body it signs: R008. Configured in Sanity's project settings,
    /// as `docs/decisions/0021-content-from-sanity.md` describes.
    ///
    /// @param signature the `sanity-webhook-signature` header
    /// @param body      the body as sent, which the signature covers
    /// @return 204, or 401 if the signature does not match
    @PostMapping("/api/sanity/webhook")
    public ResponseEntity<Void> webhook(
            @RequestHeader(name = "sanity-webhook-signature", required = false) @Nullable String signature,
            @RequestBody(required = false) byte @Nullable [] body) {
        content.published(signature, body == null ? new byte[0] : body);
        return ResponseEntity.noContent().build();
    }
}
