package se.teaterihuskvarna.mailing;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/// What Brevo says about one campaign: R025.
///
/// @param status          Brevo's status, such as `draft`, `queued` or `sent`
/// @param sentAt          when Brevo sent it, or null before then
/// @param sent            mails sent
/// @param delivered       mails delivered
/// @param uniqueViews     recipients who opened it
/// @param unsubscriptions recipients who unsubscribed from it
/// @param hardBounces     addresses that do not exist
public record CampaignReport(String status, @Nullable Instant sentAt, long sent, long delivered, long uniqueViews,
        long unsubscriptions, long hardBounces) {
}
