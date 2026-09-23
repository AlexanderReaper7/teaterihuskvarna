package se.teaterihuskvarna.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.Instant;

/// A member's login, and the address login links and mailings go to. A member
/// has one at most, and a member added to a household has none until they
/// accept an invitation. An administrator account is a different thing, in
/// `se.teaterihuskvarna.administrator`.
///
/// Column lengths mirror `V2__login.sql` by hand, because `ddl-auto: validate`
/// does not compare them: `docs/decisions/0012-jpa-over-a-schema-flyway-owns.md`.
@Entity
@Table(name = "account")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id")
    private Member member;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Account() {
        // for JPA
    }

    /// @param member the member this account logs in as
    /// @param email  the address, already normalised, unique case insensitively across accounts
    public Account(Member member, String email) {
        this.member = member;
        this.email = email;
    }

    public Long getId() {
        return id;
    }

    public Member getMember() {
        return member;
    }

    public String getEmail() {
        return email;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
