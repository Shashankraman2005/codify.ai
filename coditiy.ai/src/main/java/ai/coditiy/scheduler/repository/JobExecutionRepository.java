package ai.coditiy.scheduler.repository;

import ai.coditiy.scheduler.model.JobExecution;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface JobExecutionRepository extends JpaRepository<JobExecution, Long> {
    List<JobExecution> findByJobId(Long jobId);
}
