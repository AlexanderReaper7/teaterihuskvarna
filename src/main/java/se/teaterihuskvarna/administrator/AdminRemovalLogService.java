package se.teaterihuskvarna.administrator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
/// Write the history of administrator removals: one row per removal and per
/// restore. Names and device are copied into the row, not looked up when the log
/// is read, becuase `administrator.rectivate()` can change a name and a log must
/// not change afterwards.
@Service
public class AdminRemovalLogService{

    private static final String UNKNOWN_DEVICE = "unknown";

    private final AdminRemovalLogRepository repository;
    private final AdministratorRepository administrators;

    AdminRemovalLogService(AdminRemovalLogRepository repository, AdministratorRepository administrators) {
        this.repository = repository;
        this.administrators = administrators;
    }
    /// @param removed the administrator who was removed
    /// @param actorId the id of the administrator who removed them
    @Transactional
    public void recordRemoval(Administrator removed, long actorId) {
        record(removed, actorId, "Removed");
    }
    /// @param restored the administrator who became active again
    /// @param actorId the id of the administrator who added them
    @Transactional
    public void recordRestore(Administrator restored, long actorId) {
        record(restored, actorId, "Restored");
    }
    private void record(Administrator subject, long actorId, String event) {
        Administrator actor = administrators.findActiveById(actorId)
            .orElseThrow(NoSuchAdministrator::new);
        repository.save(new AdminRemovalLog(
                subject.getId(), subject.getFullName(), actorId, actor.getFullName(), event, UNKNOWN_DEVICE ));
    }
}
