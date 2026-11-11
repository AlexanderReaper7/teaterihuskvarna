package se.teaterihuskvarna.administrator;
import org.springframework.stereotype.Service;

@Service
public record AdminRemovalLogService(AdminRemovalLogRepository repository){

    public void recordRemoval(Long administratorId, Long actorId){
        repository.save(new AdminRemovalLog(administratorId, actorId,"Removed"));
    }
    public void recordRestore(Long administratorId, Long actorId){
        repository.save(new AdminRemovalLog(administratorId, actorId, "Restored"));
    }
}
