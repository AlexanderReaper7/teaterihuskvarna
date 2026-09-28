package se.teaterihuskvarna.mailing;

import java.time.Instant;
import org.jspecify.annotations.Nullable;

/// One row of the mailing log: R025.
///
/// @param id              the mailing
/// @param subject         the subject line
/// @param audienceName    who it was for, in Swedish
/// @param recipients      how many addresses Brevo's list got
/// @param brevoCampaignId the campaign in Brevo, where the administrator sends it
/// @param createdAt       when it was prepared
/// @param status          Brevo's status when last asked, such as `draft` or `sent`
/// @param sentAt          when Brevo sent it, or null
/// @param sent            mails sent
/// @param delivered       mails delivered
/// @param uniqueViews     recipients who opened it
/// @param unsubscriptions recipients who unsubscribed from it
/// @param hardBounces     addresses that do not exist
/// @param checkedAt       when Brevo was last asked
public record MailingDetails(long id, String subject, String audienceName, int recipients, long brevoCampaignId,
        Instant createdAt, String status, @Nullable Instant sentAt, int sent, int delivered, int uniqueViews,
        int unsubscriptions, int hardBounces, Instant checkedAt) {

    static MailingDetails of(Mailing mailing) {
        return new MailingDetails(mailing.getId(), mailing.getSubject(), mailing.getAudienceName(),
                mailing.getRecipients(), mailing.getBrevoCampaignId(), mailing.getCreatedAt(), mailing.getStatus(),
                mailing.getSentAt(), mailing.getSent(), mailing.getDelivered(), mailing.getUniqueViews(),
                mailing.getUnsubscriptions(), mailing.getHardBounces(), mailing.getCheckedAt());
    }
}
