package se.teaterihuskvarna.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/// One fee payment for one year, as an administrator marked it.
///
/// The member, the administrator and the household are plain ids rather than
/// associations.
/// Both go NULL in the database when their row is deleted, and the fee is then
/// an anonymous line in the year's income, which nothing needs to navigate
/// from. Column lengths mirror `V6__fees_households_invitations.sql` by hand:
/// `docs/decisions/0012-jpa-over-a-schema-flyway-owns.md`.
@Entity
@Table(name = "fee")
public class Fee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private @Nullable Long id;

    @Column(name = "member_id")
    private @Nullable Long memberId;

    @Column(name = "year", nullable = false)
    private int year;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 10)
    private FeeKind kind;

    @Column(name = "amount_ore", nullable = false)
    private int amountOre;

    @Column(name = "paid_at", nullable = false)
    private Instant paidAt;

    @Column(name = "marked_by")
    private @Nullable Long markedBy;

    @Column(name = "household_id")
    private @Nullable Long householdId;

    protected Fee() {
        // for JPA
    }

    /// @param memberId  the member who paid
    /// @param year      the year the payment covers
    /// @param kind      whether it covers the member or their household
    /// @param amountOre the amount in öre
    /// @param paidAt    when the administrator marked it
    /// @param markedBy  the administrator who marked it
    /// @param householdId the household a [FeeKind#HOUSEHOLD] payment covers, or null
    public Fee(long memberId, int year, FeeKind kind, int amountOre, Instant paidAt, long markedBy,
            @Nullable Long householdId) {
        this.memberId = memberId;
        this.year = year;
        this.kind = kind;
        this.amountOre = amountOre;
        this.paidAt = paidAt;
        this.markedBy = markedBy;
        this.householdId = householdId;
    }

    /// @return the ID assigned by Hibernate
    /// @throws IllegalStateException if this entity has not been persisted
    public Long getId() {
        if (id == null) {
            throw new IllegalStateException("Entity has not been persisted");
        }
        return id;
    }

    /// @return the member who paid, or null once that member is deleted
    public @Nullable Long getMemberId() {
        return memberId;
    }

    public int getYear() {
        return year;
    }

    public FeeKind getKind() {
        return kind;
    }

    public int getAmountOre() {
        return amountOre;
    }

    public Instant getPaidAt() {
        return paidAt;
    }

    /// @return the household a household payment covers, whoever is in it now,
    ///         or null for an individual payment or a payer in no household
    public @Nullable Long getHouseholdId() {
        return householdId;
    }

    /// @return the administrator who marked the payment, or null once that row is deleted
    public @Nullable Long getMarkedBy() {
        return markedBy;
    }
}
