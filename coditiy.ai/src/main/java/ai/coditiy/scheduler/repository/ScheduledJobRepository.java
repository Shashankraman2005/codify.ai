package ai.coditiy.scheduler.repository;

import ai.coditiy.scheduler.model.ScheduledJob;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface ScheduledJobRepository extends JpaRepository<ScheduledJob, Long> {
    List<ScheduledJob> findByProjectId(Long projectId);
    Optional<ScheduledJob> findByProjectIdAndName(Long projectId, String name);
    List<ScheduledJob> findByIsActiveTrue();
}
