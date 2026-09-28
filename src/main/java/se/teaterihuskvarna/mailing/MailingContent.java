package se.teaterihuskvarna.mailing;

import java.util.List;
import se.teaterihuskvarna.content.Event;
import se.teaterihuskvarna.content.NewsItem;

/// The published content a mailing can include.
///
/// @param events the upcoming events, earliest first
/// @param news   the published news, newest first
public record MailingContent(List<Event> events, List<NewsItem> news) {

    /// Copies the lists, so the choice cannot change after it is made.
    public MailingContent {
        events = List.copyOf(events);
        news = List.copyOf(news);
    }
}
