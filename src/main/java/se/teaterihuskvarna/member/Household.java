package se.teaterihuskvarna.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.jspecify.annotations.Nullable;

/// A household, so one fee can cover everyone living at the same address.
@Entity
@Table(name = "household")
public class Household {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "owner_member_id")
    private Long ownerMemberId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected Household() {
        // for JPA
    }

    /// @param name what the household is called on a fee record, not a legal name
    public Household(String name) {
        this.name = name;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    /// @return the member owner, or null when administrators manage the household
    public @Nullable Long getOwnerMemberId() {
        return ownerMemberId;
    }

    /// @param memberId the member owner, or null for administrator management
    public void setOwnerMemberId(@Nullable Long memberId) {
        ownerMemberId = memberId;
    }

    /// @param name the new household name
    public void setName(String name) {
        this.name = name;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
