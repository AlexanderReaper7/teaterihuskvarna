package se.teaterihuskvarna.mailing;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.jspecify.annotations.Nullable;

/// What an administrator chooses for a mailing: R022.
///
/// @param subject  the subject line and heading
/// @param intro    the administrator's own words above the content, plain text, or blank
/// @param audience the audience's value from [MailingService#audiences], such as `PAID` or `OFFER:12`
/// @param events   the slugs of the events to include
/// @param news     the slugs of the news items to include
public record MailingForm(
        @NotBlank @Size(max = 150) String subject,
        @Nullable @Size(max = 5000) String intro,
        @NotBlank @Size(max = 40) String audience,
        @Nullable List<String> events,
        @Nullable List<String> news) {

    /// A form with no box ticked binds the lists as null, so null becomes empty.
    public MailingForm {
        events = events == null ? List.of() : List.copyOf(events);
        news = news == null ? List.of() : List.copyOf(news);
    }

    /// @return the event slugs, never null
    public List<String> eventSlugs() {
        return events == null ? List.of() : events;
    }

    /// @return the news slugs, never null
    public List<String> newsSlugs() {
        return news == null ? List.of() : news;
    }
}
