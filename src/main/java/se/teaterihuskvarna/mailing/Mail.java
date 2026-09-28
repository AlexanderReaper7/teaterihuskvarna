package se.teaterihuskvarna.mailing;

import java.util.List;
import org.jspecify.annotations.Nullable;

/// Everything `mail/mailing.jte` prints, with every text already in Swedish and
/// every link absolute, since a mail has no site to be relative to.
///
/// @param subject the subject, also the heading
/// @param intro   the administrator's own words above the content, or null
/// @param events  the chosen events
/// @param news    the chosen news items
/// @param labels  the fixed texts
public record Mail(String subject, @Nullable String intro, List<Item> events, List<Item> news, Labels labels) {

    /// Copies the lists, so a mail cannot change after it is made.
    public Mail {
        events = List.copyOf(events);
        news = List.copyOf(news);
    }

    /// One event or news item.
    ///
    /// @param title    its heading
    /// @param when     the date, or date and time, in Swedish
    /// @param place    where, or null
    /// @param summary  the ingress, or null
    /// @param bodyHtml a news item's text as escaped HTML, or null for an event
    /// @param imageUrl a picture, or null
    /// @param imageAlt the picture's text
    /// @param url      the page on the site
    public record Item(String title, String when, @Nullable String place, @Nullable String summary,
            @Nullable String bodyHtml, @Nullable String imageUrl, String imageAlt, String url) {
    }

    /// @param events      the heading above the events
    /// @param news        the heading above the news
    /// @param readMore    the link to an item's page
    /// @param site        the link to the site at the bottom
    /// @param siteUrl     the site's address
    /// @param unsubscribe the unsubscribe link's text
    /// @param why         why the member got the mail
    public record Labels(String events, String news, String readMore, String site, String siteUrl,
            String unsubscribe, String why) {
    }
}
