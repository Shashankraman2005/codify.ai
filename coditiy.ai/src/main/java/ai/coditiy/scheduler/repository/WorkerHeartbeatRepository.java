package ai.coditiy.scheduler.repository;

import ai.coditiy.scheduler.model.WorkerHeartbeat;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface WorkerHeartbeatRepository extends JpaRepository<WorkerHeartbeat, Long> {
    List<WorkerHeartbeat> findByWorkerWorkerId(String workerId);
    void deleteByWorkerWorkerId(String workerId);
}
