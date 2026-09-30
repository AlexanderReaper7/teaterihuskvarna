package se.teaterihuskvarna.member;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// Reads and writes invitations. Package private for the same reason as
/// [MemberRepository]: `docs/decisions/0014-one-service-layer-two-adapters.md`.
interface InvitationRepository extends JpaRepository<Invitation, Long> {

    /// Stores an invitation, replacing any open one for the same member, so the
    /// older link stops working. One upsert for the reason
    /// [MembershipApplicationRepository#replace] gives: two sends at once would
    /// otherwise both insert, and the second would fail on `invitation_member_id_key`.
    ///
    /// @param memberId  the member the account will belong to
    /// @param email     the address, already normalised
    /// @param tokenHash [se.teaterihuskvarna.login.Tokens#hash] of the token in the mailed link
    /// @param createdAt when the invitation was sent
    /// @param expiresAt when the link stops working
    /// @return the number of rows written, always 1
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO invitation (member_id, email, token_hash, created_at, expires_at)
            VALUES (:memberId, :email, :tokenHash, :createdAt, :expiresAt)
            ON CONFLICT (member_id) DO UPDATE SET
                email = EXCLUDED.email,
                token_hash = EXCLUDED.token_hash,
                created_at = EXCLUDED.created_at,
                expires_at = EXCLUDED.expires_at
            """)
    int replace(
            @Param("memberId") long memberId,
            @Param("email") String email,
            @Param("tokenHash") String tokenHash,
            @Param("createdAt") Instant createdAt,
            @Param("expiresAt") Instant expiresAt);

    /// Finds an invitation by its token and locks the row, so two uses of the
    /// same link run one after the other and the second finds it deleted.
    ///
    /// @param tokenHash the hash of the token from the link
    /// @return the invitation, locked until the transaction ends, or empty
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Invitation> findByTokenHash(String tokenHash);

    /// @param memberIds members
    /// @return the open invitations for those members, expired ones included
    List<Invitation> findByMemberIdIn(Collection<Long> memberIds);

    /// @param memberId a member who now has an account
    /// @return how many invitations were deleted, 0 or 1
    @Modifying
    @Query("delete from Invitation i where i.memberId = :memberId")
    int deleteByMember(@Param("memberId") long memberId);

    /// @param now the current time
    /// @return how many expired invitations were deleted
    @Modifying
    @Query("delete from Invitation i where i.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}
