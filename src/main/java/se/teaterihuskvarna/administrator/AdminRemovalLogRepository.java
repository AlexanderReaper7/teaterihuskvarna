package se.teaterihuskvarna.administrator;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
@Repository
public interface AdminRemovalLogRepository extends JpaRepository<AdminRemovalLog, Long> {


   List<AdminRemovalLog> FindByAdministratorIdOrderByOccurredAt(Long administratorId);
   List<AdminRemovalLog> FindByActorIdOrderByOccurredAt(Long actorId);


}
