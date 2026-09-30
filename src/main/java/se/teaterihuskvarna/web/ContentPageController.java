package se.teaterihuskvarna.web;

import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import se.teaterihuskvarna.content.ContentService;
import se.teaterihuskvarna.content.Event;
import se.teaterihuskvarna.content.Perspective;
import se.teaterihuskvarna.content.Previews;

/// The public pages, with content from Sanity: R001 to R004.
///
/// Every page shows drafts instead of published documents to an editor the
/// Studio's Presentation tool sent here ([Previews]), and says so in a banner.
///
/// The start page answers `/` in every profile. Developer tools have their own
/// address, `/dev`, under the `dev` profile.
@Controller
public class ContentPageController {

    private final ContentService content;
    private final Previews previews;

    ContentPageController(ContentService content, Previews previews) {
        this.content = content;
        this.previews = previews;
    }

    /// @param pass the preview cookie, if any
    /// @return drafts for an editor in the Studio's preview, published for everyone else
    @ModelAttribute("perspective")
    public Perspective perspective(@CookieValue(name = Previews.COOKIE, required = false) @Nullable String pass) {
        return previews.perspective(pass);
    }

    /// @param perspective published or drafts
    /// @param model       receives the next event, the rest of the upcoming events and the latest news
    /// @return the start page
    @GetMapping("/")
    public String home(@ModelAttribute("perspective") Perspective perspective, Model model) {
        Event next = content.nextEvent(perspective).orElse(null);
        model.addAttribute("next", next);
        model.addAttribute("events", below(content.upcomingEvents(null, perspective), next).stream().limit(6).toList());
        model.addAttribute("news", content.news(perspective).stream().limit(3).toList());
        return "content/home";
    }

    /// The calendar: R002.
    ///
    /// @param serie       a series' slug, to show only its events, or null for all
    /// @param perspective published or drafts
    /// @param model       receives the events, every series and the chosen one
    /// @return the calendar page
    @GetMapping("/kalender")
    public String calendar(@RequestParam(name = "serie", required = false) @Nullable String serie,
            @ModelAttribute("perspective") Perspective perspective, Model model) {
        String chosen = serie == null || serie.isBlank() ? null : serie;
        model.addAttribute("events", content.upcomingEvents(chosen, perspective));
        model.addAttribute("series", content.series(perspective));
        model.addAttribute("chosen", chosen);
        return "content/calendar";
    }

    /// @param slug        the event's slug
    /// @param perspective published or drafts
    /// @param model       receives the event
    /// @return the event page, or 404
    @GetMapping("/evenemang/{slug}")
    public String event(@PathVariable String slug, @ModelAttribute("perspective") Perspective perspective,
            Model model) {
        model.addAttribute("event", content.event(slug, perspective).orElseThrow(ContentPageController::notFound));
        return "content/event";
    }

    /// @param perspective published or drafts
    /// @param model       receives the news
    /// @return the news list: R003
    @GetMapping("/nyheter")
    public String news(@ModelAttribute("perspective") Perspective perspective, Model model) {
        model.addAttribute("news", content.news(perspective));
        return "content/news";
    }

    /// @param slug        the item's slug
    /// @param perspective published or drafts
    /// @param model       receives the item
    /// @return the news item, or 404
    @GetMapping("/nyheter/{slug}")
    public String newsItem(@PathVariable String slug, @ModelAttribute("perspective") Perspective perspective,
            Model model) {
        model.addAttribute("item", content.newsItem(slug, perspective).orElseThrow(ContentPageController::notFound));
        return "content/newsItem";
    }

    /// One of [ContentService#PAGES]: R004. The pattern keeps this mapping
    /// from answering for any other one-segment path.
    ///
    /// @param slug        the page's slug
    /// @param perspective published or drafts
    /// @param model       receives the page
    /// @return the page, or 404 if the editors have not written it
    @GetMapping("/{slug:om-foreningen|styrelsen|produktioner|ludde-priser|kontakt}")
    public String page(@PathVariable String slug, @ModelAttribute("perspective") Perspective perspective,
            Model model) {
        model.addAttribute("page", content.page(slug, perspective).orElseThrow(ContentPageController::notFound));
        return "content/page";
    }

    /// @param perspective published or drafts
    /// @param model       receives the partners
    /// @return the partner page: R004
    @GetMapping("/partners")
    public String partners(@ModelAttribute("perspective") Perspective perspective, Model model) {
        model.addAttribute("partners", content.partners(perspective));
        return "content/partners";
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
