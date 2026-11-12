package se.teaterihuskvarna.administrator;
import org.springframework.stereotype.Service;
/// Write the history of administrator removals: one row per removal and per
/// restore, kept apart from `administrator`becuase `Administrator.reactive()`
/// clears who removed whom.
@Service
public class AdminRemovalLogService {

    private final AdminRemovalLogRepository repository;

    AdminRemovalLogService(AdminRemovalLogRepository repository){
        this.repository = repository;
    }
    public void recordRemoval(Long administratorId, Long actorId){
        repository.save(new AdminRemovalLog(administratorId, actorId,"Removed"));
    }
    public void recordRestore(Long administratorId, Long actorId){
        repository.save(new AdminRemovalLog(administratorId, actorId, "Restored"));
    }
}
