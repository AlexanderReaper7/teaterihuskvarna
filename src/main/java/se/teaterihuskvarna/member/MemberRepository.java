package se.teaterihuskvarna.member;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/// Reads and writes members. Spring Data derives the implementation from the
/// method names, which is why `suppressions.xml` exempts this file from
/// `MethodName`: a derived query may carry underscores.
///
/// Package private on purpose. [MemberService] is the only caller, so an
/// adapter cannot reach the database without going through a capability
/// somebody named: `docs/decisions/0014-one-service-layer-two-adapters.md`.
interface MemberRepository extends JpaRepository<Member, Long> {

    /// Addresses are matched case insensitively, as `member_email_key` is.
    ///
    /// @param email the address to look up, in any case
    /// @return the member with that address, or empty
    Optional<Member> findByEmailIgnoreCase(String email);
}
