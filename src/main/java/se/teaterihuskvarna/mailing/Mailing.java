package se.teaterihuskvarna.mailing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/// A mailing prepared in Brevo, and what Brevo last said about it.
///
/// Column lengths mirror `V10__mailings.sql` and `V13__brevo_contacts.sql` by hand, because `ddl-auto: validate`
/// does not compare them: `docs/decisions/0012-jpa-over-a-schema-flyway-owns.md`.
@Entity
@Table(name = "mailing")
public class Mailing {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private @Nullable Long id;

    @Column(name = "subject", nullable = false, length = 150)
    private String subject;

    @Column(name = "audience", nullable = false, length = 40)
    private String audience;

    @Column(name = "audience_name", nullable = false, length = 300)
    private String audienceName;

    @Column(name = "brevo_campaign_id", nullable = false)
    private long brevoCampaignId;

    @Column(name = "created_by")
    private @Nullable Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Column(name = "sent_at")
    private @Nullable Instant sentAt;

    @Column(name = "sent", nullable = false)
    private int sent;

    @Column(name = "delivered", nullable = false)
    private int delivered;

    @Column(name = "unique_views", nullable = false)
    private int uniqueViews;

    @Column(name = "unsubscriptions", nullable = false)
    private int unsubscriptions;

    @Column(name = "hard_bounces", nullable = false)
    private int hardBounces;

    @Column(name = "checked_at", nullable = false)
    private Instant checkedAt;

    protected Mailing() {
        // for JPA
    }

    /// @param subject      the subject line
    /// @param audience     the audience, `LIST` or `SEGMENT:<id>`
    /// @param audienceName the audience as the form showed it
    /// @param campaignId   Brevo's draft campaign
    /// @param createdBy    the administrator who prepared it
    /// @param now          the moment it was prepared
    Mailing(String subject, String audience, String audienceName, long campaignId, long createdBy, Instant now) {
        this.subject = subject;
        this.audience = audience;
        this.audienceName = audienceName;
        this.brevoCampaignId = campaignId;
        this.createdBy = createdBy;
        this.createdAt = now;
        this.status = "draft";
        this.checkedAt = now;
    }

    /// @return the ID assigned by Hibernate
    /// @throws IllegalStateException if this entity has not been persisted
    public Long getId() {
        if (id == null) {
            throw new IllegalStateException("Entity has not been persisted");
        }
        return id;
    }

    public String getSubject() {
        return subject;
    }

    public String getAudience() {
        return audience;
    }

    public String getAudienceName() {
        return audienceName;
    }

    public long getBrevoCampaignId() {
        return brevoCampaignId;
    }

    public @Nullable Long getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getStatus() {
        return status;
    }

    public @Nullable Instant getSentAt() {
        return sentAt;
    }

    public int getSent() {
        return sent;
    }

    public int getDelivered() {
        return delivered;
    }

    public int getUniqueViews() {
        return uniqueViews;
    }

    public int getUnsubscriptions() {
        return unsubscriptions;
    }

    public int getHardBounces() {
        return hardBounces;
    }

    public Instant getCheckedAt() {
        return checkedAt;
    }

    /// Brevo stops changing a campaign once it is sent or cancelled, apart
    /// from the numbers, which settle within days.
    ///
    /// @param now the moment to ask about
    /// @return whether the log should ask Brevo again
    boolean worthChecking(Instant now) {
        boolean settled = switch (status) {
            case "sent" -> sentAt != null && sentAt.isBefore(now.minus(Duration.ofDays(7)));
            case "archive", "cancelled" -> true;
            default -> false;
        };
        return !settled && checkedAt.isBefore(now.minusSeconds(60));
    }

    /// @param report what Brevo said
    /// @param now    when it said it
    void record(CampaignReport report, Instant now) {
        this.status = report.status().length() > 20 ? report.status().substring(0, 20) : report.status();
        this.sentAt = report.sentAt();
        this.sent = (int) report.sent();
        this.delivered = (int) report.delivered();
        this.uniqueViews = (int) report.uniqueViews();
        this.unsubscriptions = (int) report.unsubscriptions();
        this.hardBounces = (int) report.hardBounces();
        this.checkedAt = now;
    }
}
