package ai.coditiy.scheduler.repository;

import ai.coditiy.scheduler.model.Queue;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface QueueRepository extends JpaRepository<Queue, Long> {
    List<Queue> findByProjectId(Long projectId);
}
