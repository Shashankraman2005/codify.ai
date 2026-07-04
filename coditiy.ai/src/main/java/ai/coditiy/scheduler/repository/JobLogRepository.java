package ai.coditiy.scheduler.repository;

import ai.coditiy.scheduler.model.JobLog;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface JobLogRepository extends JpaRepository<JobLog, Long> {
    List<JobLog> findByJobIdOrderByCreatedAtAsc(Long jobId);
}
