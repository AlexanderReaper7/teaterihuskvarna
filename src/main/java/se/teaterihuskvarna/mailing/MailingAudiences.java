package se.teaterihuskvarna.mailing;

import java.util.List;

/// The audiences a mailing can go to: the whole members' list first, then
/// each segment the association made in Brevo (R022).
///
/// @param choices             the audiences, the list first and then the segments by name
/// @param segmentsUnavailable true if Brevo could not be asked for its segments, so only the list is offered
public record MailingAudiences(List<AudienceChoice> choices, boolean segmentsUnavailable) {

    public MailingAudiences {
        choices = List.copyOf(choices);
    }
}
