package se.teaterihuskvarna.mailing;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/// Reads and writes mailings. Package private, so only [MailingService] can
/// reach the table: `docs/decisions/0014-one-service-layer-two-adapters.md`.
interface MailingRepository extends JpaRepository<Mailing, Long> {

    /// @return every mailing, newest first
    List<Mailing> findAllByOrderByCreatedAtDescIdDesc();
}
