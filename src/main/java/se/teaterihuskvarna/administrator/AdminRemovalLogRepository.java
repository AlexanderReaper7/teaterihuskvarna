package se.teaterihuskvarna.administrator;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
/// Rows of the administrator removal log, written when an administrator is
/// removed or added again. Rows are only added, never changed, becuase
/// `Administrator.reactivate()` clears who removed whom.
@Repository
public interface AdminRemovalLogRepository extends JpaRepository<AdminRemovalLog, Long> {

    List<AdminRemovalLog> findByAdministratorIdOrderByOccurredAt(Long administratorId);
    List<AdminRemovalLog> findByActorIdOrderByOccurredAt(Long actorId);
    List<AdminRemovalLog> findAllByOrderByOccurredAtDesc();
}
