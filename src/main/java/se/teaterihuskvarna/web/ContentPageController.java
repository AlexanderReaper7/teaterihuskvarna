package se.teaterihuskvarna.web;

import gg.jte.generated.precompiled.Templates;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.View;
import se.teaterihuskvarna.content.ContentService;
import se.teaterihuskvarna.content.ContentSettings;
import se.teaterihuskvarna.content.EditAttributes;
import se.teaterihuskvarna.content.Event;
import se.teaterihuskvarna.content.NewsItem;
import se.teaterihuskvarna.content.Page;
import se.teaterihuskvarna.content.Perspective;

/// The public pages, with content from Sanity: R001 to R004.
///
/// Every page shows drafts instead of published documents to an editor the
/// Studio's Presentation tool sent here ([Previews]), and says so in a banner.
/// Their elements then carry `data-sanity` attributes ([EditAttributes]), which
/// let the tool open the field an element shows in the Studio's editor.
///
/// The start page answers `/` in every profile. Developer tools have their own
/// address, `/dev`, under the `dev` profile.
@Controller
public class ContentPageController {

    private final ContentService content;
    private final PageMetadataFactory metadata;
    private final ContentSettings settings;
    private final Copy copy;
    private final Templates templates;

    ContentPageController(ContentService content, PageMetadataFactory metadata, ContentSettings settings, Copy copy,
            Templates templates) {
        this.content = content;
        this.metadata = metadata;
        this.settings = settings;
        this.copy = copy;
        this.templates = templates;
    }

    /// @param perspective published or drafts
    /// @return the start page: the next event, the rest of the upcoming events and the latest news
    @GetMapping("/")
    public View home(Perspective perspective) {
        Event next = content.nextEvent(perspective).orElse(null);
        return HtmlPage.of(templates.contentHome(copy,
                metadata.fixed("/", "home.title", "metadata.defaultDescription"), perspective, edit(perspective),
                next, below(content.upcomingEvents(null, perspective), next).stream().limit(6).toList(),
                content.news(perspective).stream().limit(3).toList()));
    }

    /// The calendar: R002.
    ///
    /// @param serie       a series' slug, to show only its events, or null for all
    /// @param perspective published or drafts
    /// @return the calendar page: the events, every series and the chosen one
    @GetMapping("/kalender")
    public View calendar(@RequestParam(name = "serie", required = false) @Nullable String serie,
            Perspective perspective) {
        String chosen = serie == null || serie.isBlank() ? null : serie;
        return HtmlPage.of(templates.contentCalendar(copy,
                metadata.fixed("/kalender", "calendar.title", "metadata.calendar"), perspective, edit(perspective),
                content.upcomingEvents(chosen, perspective), content.series(perspective), chosen));
    }

    /// @param slug        the event's slug
    /// @param perspective published or drafts
    /// @return the event page, or 404
    @GetMapping("/evenemang/{slug}")
    public View event(@PathVariable String slug, Perspective perspective) {
        Event event = content.event(slug, perspective).orElseThrow(ContentPageController::notFound);
        PageMetadata described = metadata.create("/evenemang/" + event.slug(), event.title(),
                event.summary() == null ? event.descriptionHtml() : event.summary(), event.image(), false);
        return HtmlPage.of(templates.contentEvent(copy, described, perspective, event, edit(perspective)));
    }

    /// @param perspective published or drafts
    /// @return the news list: R003
    @GetMapping("/nyheter")
    public View news(Perspective perspective) {
        return HtmlPage.of(templates.contentNews(copy, metadata.fixed("/nyheter", "news.title", "metadata.news"),
                perspective, edit(perspective), content.news(perspective)));
    }

    /// @param slug        the item's slug
    /// @param perspective published or drafts
    /// @return the news item, or 404
    @GetMapping("/nyheter/{slug}")
    public View newsItem(@PathVariable String slug, Perspective perspective) {
        NewsItem item = content.newsItem(slug, perspective).orElseThrow(ContentPageController::notFound);
        PageMetadata described = metadata.create("/nyheter/" + item.slug(), item.title(),
                item.summary() == null ? item.bodyHtml() : item.summary(), item.image(), true);
        return HtmlPage.of(templates.contentNewsItem(copy, described, perspective, item, edit(perspective)));
    }

    /// One of [ContentService#PAGES]: R004. The pattern keeps this mapping
    /// from answering for any other one-segment path. Produktioner, Styrelsen
    /// and Ludde-priser list their documents below the page's own text, and
    /// the other pages are text only.
    ///
    /// @param slug        the page's slug
    /// @param perspective published or drafts
    /// @return the page with the list it shows, if any, or 404 if the editors have not written it
    @GetMapping("/{slug:om-foreningen|styrelsen|produktioner|ludde-priser|kontakt}")
    public View page(@PathVariable String slug, Perspective perspective) {
        Page page = content.page(slug, perspective).orElseThrow(ContentPageController::notFound);
        PageMetadata described = metadata.create("/" + slug, page.title(), page.bodyHtml(), page.image(), false);
        return HtmlPage.of(templates.contentPage(copy, described, perspective, page, edit(perspective),
                "produktioner".equals(slug) ? content.productions(perspective) : List.of(),
                "styrelsen".equals(slug) ? content.board(perspective) : List.of(),
                "ludde-priser".equals(slug) ? content.luddePrizes(perspective) : List.of()));
    }

    /// @param perspective published or drafts
    /// @return the partner page: R004
    @GetMapping("/partners")
    public View partners(Perspective perspective) {
        return HtmlPage.of(templates.contentPartners(copy,
                metadata.fixed("/partners", "partners.title", "metadata.partners"), perspective, edit(perspective),
                content.partners(perspective)));
    }

    /// The `data-sanity` attributes the templates write, which are all null
    /// outside a preview.
    private EditAttributes edit(Perspective perspective) {
        return EditAttributes.of(perspective, settings.studioUrl());
    }

    private static ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND);
    }

    /// The start page's list below the event it leads with: the events
    /// starting with it or later, other than it.
    ///
    /// @param upcoming the upcoming events, earliest first
    /// @param next     the event the page leads with, or null
    /// @return the events for the list
    static List<Event> below(List<Event> upcoming, @Nullable Event next) {
        if (next == null) {
            return upcoming;
        }
        return upcoming.stream()
                .filter(event -> !event.id().equals(next.id()) && !event.startsAt().isBefore(next.startsAt()))
                .toList();
    }
}
