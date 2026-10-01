package se.teaterihuskvarna.administrator;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;
import se.teaterihuskvarna.login.Email;

/// An administrator account. Not an account in the member sense: an
/// administrator need not be a member, and one address may belong to both.
///
/// A removed administrator keeps the row, with `removedAt` set, because fee
/// records will name who marked them paid. `createdBy` and `removedBy` are plain
/// ids rather than associations: nothing here needs to load the administrator
/// they name, and an association would be one more lazy proxy to trip over
/// outside a transaction.
///
/// Column lengths mirror `V2__login.sql` by hand, because `ddl-auto: validate`
/// does not compare them: `docs/decisions/0012-jpa-over-a-schema-flyway-owns.md`.
@Entity
@Table(name = "administrator")
public class Administrator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private @Nullable Long id;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "created_by")
    private @Nullable Long createdBy;

    @Column(name = "removed_at")
    private @Nullable Instant removedAt;

    @Column(name = "removed_by")
    private @Nullable Long removedBy;

    protected Administrator() {
        // for JPA
    }

    /// @param email     the address, unique case insensitively across every row
    /// @param fullName  the name to greet them by
    /// @param createdBy the administrator who added this one, or null for the first administrator
    public Administrator(Email email, String fullName, @Nullable Long createdBy) {
        this.email = email.value();
        this.fullName = fullName;
        this.createdBy = createdBy;
    }

    /// @return the ID assigned by Hibernate
    /// @throws IllegalStateException if this entity has not been persisted
    public Long getId() {
        if (id == null) {
            throw new IllegalStateException("Entity has not been persisted");
        }
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getFullName() {
        return fullName;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public @Nullable Long getCreatedBy() {
        return createdBy;
    }

    public @Nullable Instant getRemovedAt() {
        return removedAt;
    }

    public @Nullable Long getRemovedBy() {
        return removedBy;
    }

    /// @return whether this administrator can log in, that is, has not been removed
    public boolean isActive() {
        return removedAt == null;
    }

    /// @param by the administrator doing the removing
    public void remove(long by) {
        this.removedAt = Instant.now();
        this.removedBy = by;
    }

    /// Makes a removed administrator active again, as if newly added. The row is
    /// reused rather than a new one inserted, because `administrator_email_key`
    /// spans removed rows too.
    ///
    /// @param newFullName the name given when re-adding, which may differ from the old one
    /// @param by          the administrator doing the adding
    public void reactivate(String newFullName, long by) {
        this.fullName = newFullName;
        this.createdAt = Instant.now();
        this.createdBy = by;
        this.removedAt = null;
        this.removedBy = null;
    }
}
