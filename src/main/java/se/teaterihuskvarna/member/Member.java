package se.teaterihuskvarna.member;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/// A member of the association. The email address is on the [Account], because
/// a member added to a household may have none.
///
/// Column lengths mirror `V1__member_register.sql` by hand, here and in [ContactDetails]. Nothing enforces
/// that: `ddl-auto: validate` catches a missing table or column, but it does not
/// compare lengths, which was measured rather than assumed. See
/// `docs/decisions/0012-jpa-over-a-schema-flyway-owns.md`.
///
/// No personal identity number, by `docs/projektplan.md`.
@Entity
@Table(name = "member")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private @Nullable Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "household_id")
    private @Nullable Household household;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Embedded
    private @Nullable ContactDetails contact;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Member() {
        // for JPA
    }

    /// @param fullName the member's name as the association writes it
    public Member(String fullName) {
        this.fullName = fullName;
    }

    /// @return the ID assigned by Hibernate
    /// @throws IllegalStateException if this entity has not been persisted
    public Long getId() {
        if (id == null) {
            throw new IllegalStateException("Entity has not been persisted");
        }
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    /// @param fullName the new name
    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public @Nullable Household getHousehold() {
        return household;
    }

    /// @param household the household whose fee covers this member, or null for none
    public void setHousehold(@Nullable Household household) {
        this.household = household;
    }

    /// @return the member's phone and postal address, [ContactDetails#NONE] if none is known
    public ContactDetails getContact() {
        return contact == null ? ContactDetails.NONE : contact;
    }

    /// @param contact the new phone and postal address
    public void setContact(ContactDetails contact) {
        this.contact = contact;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
