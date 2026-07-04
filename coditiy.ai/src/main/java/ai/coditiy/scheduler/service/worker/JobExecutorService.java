package ai.coditiy.scheduler.service.worker;

import ai.coditiy.scheduler.model.*;
import ai.coditiy.scheduler.repository.DeadLetterQueueRepository;
import ai.coditiy.scheduler.repository.JobExecutionRepository;
import ai.coditiy.scheduler.repository.JobRepository;
import ai.coditiy.scheduler.service.JobService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import ai.coditiy.scheduler.service.ws.JobBroadcastService;
import ai.coditiy.scheduler.service.ws.EventBroadcastService;
import ai.coditiy.scheduler.service.ws.DashboardBroadcastService;
import ai.coditiy.scheduler.dto.JobResponse;

@Service
@Slf4j
public class JobExecutorService {

    private final JobRepository jobRepository;
    private final JobBroadcastService jobBroadcastService;
    private final EventBroadcastService eventBroadcastService;
    private final DashboardBroadcastService dashboardBroadcastService;
    private final JobExecutionRepository jobExecutionRepository;
    private final DeadLetterQueueRepository dlqRepository;
    private final JobService jobService;
    private final RetryBackoffCalculator retryBackoffCalculator;

    public JobExecutorService(
        JobRepository jobRepository,
        JobBroadcastService jobBroadcastService,
        EventBroadcastService eventBroadcastService,
        DashboardBroadcastService dashboardBroadcastService,
        JobExecutionRepository jobExecutionRepository,
        DeadLetterQueueRepository dlqRepository,
        JobService jobService,
        RetryBackoffCalculator retryBackoffCalculator
    ) {
        this.jobRepository = jobRepository;
        this.jobBroadcastService = jobBroadcastService;
        this.eventBroadcastService = eventBroadcastService;
        this.dashboardBroadcastService = dashboardBroadcastService;
        this.jobExecutionRepository = jobExecutionRepository;
        this.dlqRepository = dlqRepository;
        this.jobService = jobService;
        this.retryBackoffCalculator = retryBackoffCalculator;
    }

    @org.springframework.context.annotation.Lazy
    @org.springframework.beans.factory.annotation.Autowired
    private JobExecutorService self;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void executeJob(Long jobId, String workerId) {
        Job job = jobRepository.findById(jobId).orElse(null);
        if (job == null) return;

        try {
            self.transitionToRunning(jobId, workerId);
            
            // Refetch job with updated state
            job = jobRepository.findById(jobId).orElseThrow();
            
            // Execute simulated task
            performWork(job);
            
            self.transitionToCompleted(jobId, workerId);
        } catch (Exception e) {
            log.error("Execution failed for job {}: {}", jobId, e.getMessage());
            self.handleExecutionFailure(jobId, workerId, e);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void transitionToRunning(Long jobId, String workerId) {
        Job job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus(JobStatus.RUNNING);
        job.setStartedAt(LocalDateTime.now());
        jobRepository.save(job);

        // Update latest execution record
        List<JobExecution> execs = jobExecutionRepository.findByJobId(jobId);
        if (!execs.isEmpty()) {
            JobExecution latest = execs.get(execs.size() - 1);
            latest.setStatus("RUNNING");
            latest.setStartedAt(LocalDateTime.now());
            jobExecutionRepository.save(latest);
        }

        jobService.logEvent(job, "INFO", "Job status updated to RUNNING on worker " + workerId);
        JobResponse response = jobService.mapToResponse(job);
        jobBroadcastService.broadcastJobUpdate(response);
        dashboardBroadcastService.broadcastDashboardMetrics(job.getQueue().getProject().getId());
        eventBroadcastService.broadcastEvent("Job " + job.getId() + " claimed by " + workerId, "JOB");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void transitionToCompleted(Long jobId, String workerId) {
        Job job = jobRepository.findById(jobId).orElseThrow();
        job.setStatus(JobStatus.COMPLETED);
        job.setCompletedAt(LocalDateTime.now());
        jobRepository.save(job);

        List<JobExecution> execs = jobExecutionRepository.findByJobId(jobId);
        if (!execs.isEmpty()) {
            JobExecution latest = execs.get(execs.size() - 1);
            latest.setStatus("COMPLETED");
            latest.setEndedAt(LocalDateTime.now());
            jobExecutionRepository.save(latest);
        }

        jobService.logEvent(job, "INFO", "Job COMPLETED successfully");
        JobResponse response = jobService.mapToResponse(job);
        jobBroadcastService.broadcastJobUpdate(response);
        dashboardBroadcastService.broadcastDashboardMetrics(job.getQueue().getProject().getId());
        eventBroadcastService.broadcastEvent("Job " + job.getId() + " completed successfully", "JOB");

        // Batch completion check
        checkAndCompleteBatch(job);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleExecutionFailure(Long jobId, String workerId, Exception ex) {
        Job job = jobRepository.findById(jobId).orElseThrow();
        
        List<JobExecution> execs = jobExecutionRepository.findByJobId(jobId);
        if (!execs.isEmpty()) {
            JobExecution latest = execs.get(execs.size() - 1);
            latest.setStatus("FAILED");
            latest.setEndedAt(LocalDateTime.now());
            latest.setErrorMessage(ex.getMessage());
            jobExecutionRepository.save(latest);
        }

        jobService.logEvent(job, "ERROR", "Job execution failed: " + ex.getMessage());

        int maxAttempts = job.getMaxAttempts();
        if (job.getAttemptCount() < maxAttempts) {
            // Apply Retry Policy delay
            int delaySeconds = calculateRetryDelay(job);
            LocalDateTime nextRun = LocalDateTime.now().plusSeconds(delaySeconds);
            
            job.setStatus(JobStatus.SCHEDULED);
            job.setScheduledAt(nextRun);
            jobRepository.save(job);
            
            jobService.logEvent(job, "INFO", "Job rescheduled to SCHEDULED for retry in " + delaySeconds + " seconds (at " + nextRun + ")");
        } else {
            // Move to Dead Letter Queue
            job.setStatus(JobStatus.DEAD_LETTER);
            job.setCompletedAt(LocalDateTime.now());
            jobRepository.save(job);

            DeadLetterQueue dlq = DeadLetterQueue.builder()
                    .job(job)
                    .finalError(ex.getMessage() != null ? ex.getMessage() : "Max attempts exceeded without message")
                    .build();
            dlqRepository.save(dlq);

            jobService.logEvent(job, "ERROR", "Job exceeded max attempts (" + maxAttempts + "). Moved to Dead Letter Queue");

            // Batch completion check
            checkAndCompleteBatch(job);
        }
        
        JobResponse response = jobService.mapToResponse(job);
        jobBroadcastService.broadcastJobUpdate(response);
        dashboardBroadcastService.broadcastDashboardMetrics(job.getQueue().getProject().getId());
        eventBroadcastService.broadcastEvent("Job " + job.getId() + " failed: " + job.getStatus(), "JOB");
    }

    private void performWork(Job job) throws Exception {
        if (job.getPayload() == null || job.getPayload().isBlank()) {
            // Default: do nothing
            return;
        }

        try {
            Map<?, ?> payloadMap = objectMapper.readValue(job.getPayload(), Map.class);
            
            // Simulated work sleep
            if (payloadMap.containsKey("sleep")) {
                int sleepMs = Integer.parseInt(payloadMap.get("sleep").toString());
                Thread.sleep(sleepMs);
            }

            // Simulated failure
            if (payloadMap.containsKey("fail") && Boolean.parseBoolean(payloadMap.get("fail").toString())) {
                String errorMsg = payloadMap.containsKey("error") ? payloadMap.get("error").toString() : "Simulated job failure";
                throw new RuntimeException(errorMsg);
            }
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Job execution was interrupted", ie);
        } catch (Exception e) {
            if (e instanceof RuntimeException && e.getMessage().contains("Simulated")) {
                throw e;
            }
            // If it's a parsing issue, just log it but do not fail unless specified
            log.warn("Payload was not key-value JSON, treating as raw string. Payload: {}", job.getPayload());
        }
    }

    private int calculateRetryDelay(Job job) {
        return retryBackoffCalculator.calculateDelay(job.getQueue().getRetryPolicy(), job.getAttemptCount());
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
