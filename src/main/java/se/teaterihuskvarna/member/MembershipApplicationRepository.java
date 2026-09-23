package se.teaterihuskvarna.member;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// Reads and writes membership applications. Package private for the same
/// reason as [MemberRepository]: `docs/decisions/0014-one-service-layer-two-adapters.md`.
interface MembershipApplicationRepository extends JpaRepository<MembershipApplication, Long> {

    /// Stores an application, replacing any earlier one for the same address.
    ///
    /// One upsert rather than a delete followed by an insert. A double click on
    /// the form sends two requests at once; with delete then insert, both
    /// deletes find nothing, and the second insert fails on
    /// `membership_application_email_key` and shows the applicant an error page
    /// for a submission that worked. `ON CONFLICT` makes the second request wait
    /// for the first and then overwrite its row.
    ///
    /// @param fullName   the applicant's name
    /// @param email      the address, already normalised
    /// @param phone      the phone number, or null
    /// @param address    the street address, or null
    /// @param postalCode the postal code, or null
    /// @param city       the city, or null
    /// @param tokenHash  [se.teaterihuskvarna.login.Tokens#hash] of the token in the mailed link
    /// @param createdAt  when the application was made
    /// @param expiresAt  when the link stops working and the row may be deleted
    /// @return the number of rows written, always 1
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO membership_application
                (full_name, email, phone, address, postal_code, city, token_hash, created_at, expires_at)
            VALUES
                (:fullName, :email, :phone, :address, :postalCode, :city, :tokenHash, :createdAt, :expiresAt)
            ON CONFLICT ((LOWER(email))) DO UPDATE SET
                full_name = EXCLUDED.full_name,
                email = EXCLUDED.email,
                phone = EXCLUDED.phone,
                address = EXCLUDED.address,
                postal_code = EXCLUDED.postal_code,
                city = EXCLUDED.city,
                token_hash = EXCLUDED.token_hash,
                created_at = EXCLUDED.created_at,
                expires_at = EXCLUDED.expires_at
            """)
    int replace(
            @Param("fullName") String fullName,
            @Param("email") String email,
            @Param("phone") @Nullable String phone,
            @Param("address") @Nullable String address,
            @Param("postalCode") @Nullable String postalCode,
            @Param("city") @Nullable String city,
            @Param("tokenHash") String tokenHash,
            @Param("createdAt") Instant createdAt,
            @Param("expiresAt") Instant expiresAt);

    /// Finds an application by its token and locks the row (`FOR UPDATE`), so
    /// that two confirmations of the same link run one after the other. The
    /// second waits, finds the row deleted by the first, and gets nothing.
    ///
    /// @param tokenHash the hash of the token from the link
    /// @return the application, locked until the transaction ends, or empty
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<MembershipApplication> findByTokenHash(String tokenHash);

    /// @param now the current time
    /// @return how many expired applications were deleted
    @Modifying
    @Query("delete from MembershipApplication a where a.expiresAt <= :now")
    int deleteExpired(@Param("now") Instant now);
}
