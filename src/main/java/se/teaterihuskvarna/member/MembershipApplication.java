package se.teaterihuskvarna.member;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/// A Bli medlem submission nobody has confirmed yet. It becomes a [Member] with
/// an [Account] when the applicant follows the link in the confirmation mail,
/// and it is deleted after its lifetime if they never do.
///
/// Kept out of `member` so that a bot filling the form never reaches the
/// register. The token is stored only as its hash, as for login links.
///
/// No public constructor. [MembershipApplicationRepository#replace] writes rows
/// with SQL, because a second application for the same address has to replace
/// the first in one statement: see that method.
///
/// Column lengths mirror `V2__login.sql` by hand, here and in [ContactDetails], because `ddl-auto: validate`
/// does not compare them: `docs/decisions/0012-jpa-over-a-schema-flyway-owns.md`.
@Entity
@Table(name = "membership_application")
public class MembershipApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Embedded
    private ContactDetails contact;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected MembershipApplication() {
        // for JPA
    }

    public Long getId() {
        return id;
    }

    public String getFullName() {
        return fullName;
    }

    public String getEmail() {
        return email;
    }

    /// @return the phone and postal address the applicant gave, [ContactDetails#NONE] if none
    public ContactDetails getContact() {
        return contact == null ? ContactDetails.NONE : contact;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
