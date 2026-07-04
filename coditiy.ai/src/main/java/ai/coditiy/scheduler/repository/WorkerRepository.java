package ai.coditiy.scheduler.repository;

import ai.coditiy.scheduler.model.Worker;
import ai.coditiy.scheduler.model.WorkerStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDateTime;
import java.util.List;

public interface WorkerRepository extends JpaRepository<Worker, String> {
    List<Worker> findByStatus(WorkerStatus status);
    List<Worker> findByStatusAndLastHeartbeatAtBefore(WorkerStatus status, LocalDateTime threshold);
}
