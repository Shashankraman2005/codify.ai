package ai.coditiy.scheduler.service.worker;

import ai.coditiy.scheduler.model.*;
import ai.coditiy.scheduler.repository.DeadLetterQueueRepository;
import ai.coditiy.scheduler.repository.JobRepository;
import ai.coditiy.scheduler.repository.WorkerRepository;
import ai.coditiy.scheduler.service.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ai.coditiy.scheduler.service.ws.WorkerBroadcastService;
import ai.coditiy.scheduler.service.ws.JobBroadcastService;
import ai.coditiy.scheduler.service.ws.EventBroadcastService;
import ai.coditiy.scheduler.service.ws.DashboardBroadcastService;
import ai.coditiy.scheduler.dto.JobResponse;
import java.time.LocalDateTime;
import java.util.List;

@Service
@Profile("worker")
@RequiredArgsConstructor
@Slf4j
public class ReaperService {

    private final LockService lockService;
    private final WorkerRepository workerRepository;
    private final JobRepository jobRepository;
    private final DeadLetterQueueRepository dlqRepository;
    private final JobService jobService;
    private final JobExecutorService executorService;
    private final WorkerBroadcastService workerBroadcastService;
    private final JobBroadcastService jobBroadcastService;
    private final EventBroadcastService eventBroadcastService;
    private final DashboardBroadcastService dashboardBroadcastService;

    @Value("${scheduler.worker.id}")
    private String workerId;

    @Value("${scheduler.reaper.dead-threshold-ms:30000}")
    private long deadThresholdMs;

    @Value("${scheduler.reaper.claim-timeout-ms:60000}")
    private long claimTimeoutMs;

    @Value("${scheduler.reaper.enabled:true}")
    private boolean reaperEnabled;

    @Scheduled(fixedDelayString = "${scheduler.reaper.interval-ms:30000}")
    public void runReaper() {
        if (!reaperEnabled) return;

        log.debug("Attempting to acquire reaper lock...");
        // Lease lock for 35 seconds (reaper runs every 30s)
        boolean lockAcquired = lockService.acquireLock("REAPER_LOCK", workerId, 35);
        if (!lockAcquired) {
            log.debug("Failed to acquire reaper lock. Another worker is currently the leader.");
            return;
        }

        log.info("Leader lock acquired. Running dead-worker reaper process...");
        try {
            recoverDeadWorkers();
            recoverStuckClaimedJobs();
        } catch (Exception e) {
            log.error("Error occurred during reaper execution", e);
        }
    }

    @Transactional
    public void recoverDeadWorkers() {
        LocalDateTime threshold = LocalDateTime.now().minusNanos(deadThresholdMs * 1_000_000);
        List<Worker> deadWorkers = workerRepository.findByStatusAndLastHeartbeatAtBefore(WorkerStatus.ACTIVE, threshold);

        if (deadWorkers.isEmpty()) {
            return;
        }

        log.info("Reaper detected {} dead workers", deadWorkers.size());

        for (Worker worker : deadWorkers) {
            log.info("Processing recovery for dead worker: {}", worker.getWorkerId());
            worker.setStatus(WorkerStatus.DEAD);
            workerRepository.save(worker);
            workerBroadcastService.broadcastWorkerUpdate(worker);
            eventBroadcastService.broadcastEvent("Worker " + worker.getWorkerId() + " died and was reaped", "WORKER");

            // Find all active jobs assigned to this worker
            List<Job> activeJobs = jobRepository.findActiveJobsByWorkerId(worker.getWorkerId());
            log.info("Worker {} was executing {} active jobs", worker.getWorkerId(), activeJobs.size());

            for (Job job : activeJobs) {
                if (job.getAttemptCount() < job.getMaxAttempts()) {
                    // Requeue job immediately
                    job.setStatus(JobStatus.QUEUED);
                    job.setWorkerId(null);
                    job.setScheduledAt(LocalDateTime.now());
                    jobRepository.save(job);
                    
                    JobResponse response = jobService.mapToResponse(job);
                    jobBroadcastService.broadcastJobUpdate(response);
                    dashboardBroadcastService.broadcastDashboardMetrics(job.getQueue().getProject().getId());
                    eventBroadcastService.broadcastEvent("Job " + job.getId() + " re-queued from dead worker", "JOB");
                    
                    jobService.logEvent(job, "WARN", "Reaper recovered job from dead worker " + worker.getWorkerId() + 
                            ". Re-queued job (Attempt " + job.getAttemptCount() + "/" + job.getMaxAttempts() + ")");
                } else {
                    // Exceeded max attempts, move to DLQ
                    job.setStatus(JobStatus.DEAD_LETTER);
                    job.setCompletedAt(LocalDateTime.now());
                    jobRepository.save(job);
                    
                    JobResponse response = jobService.mapToResponse(job);
                    jobBroadcastService.broadcastJobUpdate(response);
                    dashboardBroadcastService.broadcastDashboardMetrics(job.getQueue().getProject().getId());
                    eventBroadcastService.broadcastEvent("Job " + job.getId() + " dead-lettered from dead worker", "JOB");

                    DeadLetterQueue dlq = DeadLetterQueue.builder()
                            .job(job)
                            .finalError("Worker node " + worker.getWorkerId() + " died while running job, and max attempts exceeded.")
                            .build();
                    dlqRepository.save(dlq);

                    jobService.logEvent(job, "ERROR", "Reaper recovered job from dead worker " + worker.getWorkerId() + 
                            ". Job exceeded max attempts. Moved to Dead Letter Queue.");
                    
                    // Batch completion check
                    checkAndCompleteBatch(job);
                }
            }
        }
    }

    @Transactional
    public void recoverStuckClaimedJobs() {
        LocalDateTime threshold = LocalDateTime.now().minusNanos(claimTimeoutMs * 1_000_000);
        List<Job> stuckJobs = jobRepository.findStuckClaimedJobs(threshold);

        if (!stuckJobs.isEmpty()) {
            log.info("Reaper detected {} jobs stuck in CLAIMED state for over {} ms", stuckJobs.size(), claimTimeoutMs);
            for (Job job : stuckJobs) {
                job.setStatus(JobStatus.QUEUED);
                job.setWorkerId(null);
                job.setScheduledAt(LocalDateTime.now());
                jobRepository.save(job);

                jobService.logEvent(job, "WARN", "Reaper detected job stuck in CLAIMED state (failed to move to RUNNING). Re-queued job.");
            }
        }
    }

    private void checkAndCompleteBatch(Job childJob) {
        if (childJob.getParentJob() == null) return;
        Job parent = childJob.getParentJob();
        
        List<Job> siblings = jobRepository.findByParentJobId(parent.getId());
        boolean allFinished = true;
        boolean anyFailed = false;

        for (Job sibling : siblings) {
            JobStatus status = sibling.getStatus();
            if (status == JobStatus.QUEUED || status == JobStatus.SCHEDULED || 
                status == JobStatus.CLAIMED || status == JobStatus.RUNNING) {
                allFinished = false;
                break;
            }
            if (status == JobStatus.FAILED || status == JobStatus.DEAD_LETTER) {
                anyFailed = true;
            }
        }

        if (allFinished) {
            if (anyFailed) {
                parent.setStatus(JobStatus.DEAD_LETTER);
                parent.setCompletedAt(LocalDateTime.now());
                jobRepository.save(parent);
                jobService.logEvent(parent, "ERROR", "Batch parent failed because one or more child jobs failed permanently");
            } else {
                parent.setStatus(JobStatus.COMPLETED);
                parent.setCompletedAt(LocalDateTime.now());
                jobRepository.save(parent);
                jobService.logEvent(parent, "INFO", "Batch parent completed successfully (all child jobs completed)");
            }
        }
    }
}
