package se.teaterihuskvarna.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/// An open invitation for a member without an account to create one. The
/// member is a plain id: the database deletes the row with the member (`ON
/// DELETE CASCADE`), and the service loads the member itself when the link is
/// used. Column lengths mirror `V6__fees_households_invitations.sql` by hand.
@Entity
@Table(name = "invitation")
public class Invitation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private @Nullable Long id;

    @Column(name = "member_id", nullable = false)
    private long memberId;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "token_hash", nullable = false, length = 64)
    private String tokenHash;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    protected Invitation() {
        // for JPA
    }

    /// @param memberId  the member the account will belong to
    /// @param email     the address the account will have, already normalised
    /// @param tokenHash [se.teaterihuskvarna.login.Tokens#hash] of the token in the mailed link
    /// @param createdAt when the invitation was sent
    /// @param expiresAt when the link stops working
    public Invitation(long memberId, String email, String tokenHash, Instant createdAt, Instant expiresAt) {
        this.memberId = memberId;
        this.email = email;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    /// @return the ID assigned by Hibernate
    /// @throws IllegalStateException if this entity has not been persisted
    public Long getId() {
        if (id == null) {
            throw new IllegalStateException("Entity has not been persisted");
        }
        return id;
    }

    public long getMemberId() {
        return memberId;
    }

    public String getEmail() {
        return email;
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
