package se.teaterihuskvarna.member;

import org.springframework.data.jpa.repository.JpaRepository;

/// Reads and writes members. Spring Data derives the implementation from the
/// method names, which is why `suppressions.xml` exempts this file from
/// `MethodName`: a derived query may carry underscores.
///
/// Package private on purpose. Only the application services in this package
/// can call it, so an adapter cannot reach the database without going through a
/// capability somebody named: `docs/decisions/0014-one-service-layer-two-adapters.md`.
///
/// No lookup by address here. The address is on the [Account], so a member is
/// found by address through [AccountRepository].
interface MemberRepository extends JpaRepository<Member, Long> {
}
