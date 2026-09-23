package se.teaterihuskvarna.administrator;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// Reads and writes administrator accounts. Package private, so only this
/// package's services can reach the table:
/// `docs/decisions/0014-one-service-layer-two-adapters.md`.
///
/// The address queries use `lower()` because `administrator_email_key` is an
/// index on `LOWER(email)`, and PostgreSQL uses an expression index only for the
/// same expression.
interface AdministratorRepository extends JpaRepository<Administrator, Long> {

    /// @return the administrators who can log in, by name
    @Query("select a from Administrator a where a.removedAt is null order by a.fullName, a.id")
    List<Administrator> findActive();

    /// The same rows as [#findActive], locked (`FOR UPDATE`) until the
    /// transaction ends. See [AdministratorService#remove] for why.
    ///
    /// @return the administrators who can log in, each row locked
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Administrator a where a.removedAt is null")
    List<Administrator> lockActive();

    /// @param email the address to look up, in any case
    /// @return the administrator row with that address, removed or not, or empty
    @Query("select a from Administrator a where lower(a.email) = lower(:email)")
    Optional<Administrator> findByEmail(@Param("email") String email);

    /// @param email the address to look up, in any case
    /// @return the administrator with that address, if not removed, or empty
    @Query("select a from Administrator a where lower(a.email) = lower(:email) and a.removedAt is null")
    Optional<Administrator> findActiveByEmail(@Param("email") String email);

    /// @param id the administrator's id
    /// @return the administrator with that id, if not removed, or empty
    @Query("select a from Administrator a where a.id = :id and a.removedAt is null")
    Optional<Administrator> findActiveById(@Param("id") long id);
}
