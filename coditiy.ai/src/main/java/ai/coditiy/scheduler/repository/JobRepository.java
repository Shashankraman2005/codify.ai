package ai.coditiy.scheduler.repository;

import ai.coditiy.scheduler.model.Job;
import ai.coditiy.scheduler.model.JobStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface JobRepository extends JpaRepository<Job, Long>, JpaSpecificationExecutor<Job> {

    @Query(value = "SELECT * FROM jobs " +
                   "WHERE status = 'QUEUED' " +
                   "AND queue_id = :queueId " +
                   "AND scheduled_at <= :now " +
                   "ORDER BY priority DESC, created_at ASC " +
                   "LIMIT 1 " +
                   "FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<Job> claimNextJob(@Param("queueId") Long queueId, @Param("now") LocalDateTime now);

    @Modifying
    @Query("UPDATE Job j SET j.status = ai.coditiy.scheduler.model.JobStatus.QUEUED " +
           "WHERE j.status = ai.coditiy.scheduler.model.JobStatus.SCHEDULED " +
           "AND j.scheduledAt <= :now")
    int promoteScheduledJobs(@Param("now") LocalDateTime now);

    long countByQueueIdAndStatus(Long queueId, JobStatus status);

    @Query(value = "SELECT EXTRACT(HOUR FROM completed_at) as hr, COUNT(*) as count FROM jobs " +
                   "WHERE queue_id IN (SELECT id FROM queues WHERE project_id = :projectId) " +
                   "AND status = 'COMPLETED' AND completed_at >= NOW() - INTERVAL '24 hours' " +
                   "GROUP BY EXTRACT(HOUR FROM completed_at)", nativeQuery = true)
    List<Object[]> getHourlyThroughputCompleted(@Param("projectId") Long projectId);

    @Query(value = "SELECT EXTRACT(HOUR FROM completed_at) as hr, COUNT(*) as count FROM jobs " +
                   "WHERE queue_id IN (SELECT id FROM queues WHERE project_id = :projectId) " +
                   "AND status = 'DEAD_LETTER' AND completed_at >= NOW() - INTERVAL '24 hours' " +
                   "GROUP BY EXTRACT(HOUR FROM completed_at)", nativeQuery = true)
    List<Object[]> getHourlyThroughputFailed(@Param("projectId") Long projectId);

    @Query(value = "SELECT AVG(EXTRACT(EPOCH FROM (completed_at - started_at))) FROM jobs " +
                   "WHERE queue_id = :queueId AND status = 'COMPLETED'", nativeQuery = true)
    Double getAverageExecutionTimeSeconds(@Param("queueId") Long queueId);

    List<Job> findByParentJobId(Long parentJobId);

    @Query("SELECT j FROM Job j WHERE j.workerId = :workerId AND j.status IN (ai.coditiy.scheduler.model.JobStatus.CLAIMED, ai.coditiy.scheduler.model.JobStatus.RUNNING)")
    List<Job> findActiveJobsByWorkerId(@Param("workerId") String workerId);

    @Query("SELECT j FROM Job j WHERE j.status = ai.coditiy.scheduler.model.JobStatus.CLAIMED AND j.updatedAt <= :threshold")
    List<Job> findStuckClaimedJobs(@Param("threshold") LocalDateTime threshold);
}
