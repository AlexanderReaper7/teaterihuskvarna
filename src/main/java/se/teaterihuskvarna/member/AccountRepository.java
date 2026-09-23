package se.teaterihuskvarna.member;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/// Reads and writes accounts. Package private for the same reason as
/// [MemberRepository]: `docs/decisions/0014-one-service-layer-two-adapters.md`.
///
/// The address queries are written out with `lower()` rather than derived from
/// `IgnoreCase`, which Spring Data turns into `upper()`. `account_email_key` is
/// an index on `LOWER(email)`, and PostgreSQL uses an expression index only for
/// the same expression.
interface AccountRepository extends JpaRepository<Account, Long> {

    /// @param email the address to look up, in any case
    /// @return the account with that address, or empty
    @Query("select a from Account a where lower(a.email) = lower(:email)")
    Optional<Account> findByEmailIgnoreCase(@Param("email") String email);
}
