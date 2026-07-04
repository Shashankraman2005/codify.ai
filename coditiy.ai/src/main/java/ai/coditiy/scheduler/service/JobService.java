package ai.coditiy.scheduler.service;

import ai.coditiy.scheduler.dto.JobRequest;
import ai.coditiy.scheduler.dto.JobResponse;
import ai.coditiy.scheduler.model.*;
import ai.coditiy.scheduler.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import ai.coditiy.scheduler.service.ws.JobBroadcastService;
import ai.coditiy.scheduler.service.ws.EventBroadcastService;
import ai.coditiy.scheduler.service.ws.DashboardBroadcastService;
@Service
@RequiredArgsConstructor
@Slf4j
public class JobService {

    private final JobRepository jobRepository;
    private final QueueRepository queueRepository;
    private final JobLogRepository jobLogRepository;
    private final DeadLetterQueueRepository dlqRepository;
    private final JobExecutionRepository jobExecutionRepository;
    private final JobBroadcastService jobBroadcastService;
    private final EventBroadcastService eventBroadcastService;
    private final DashboardBroadcastService dashboardBroadcastService;

    @Transactional
    public JobResponse createJob(JobRequest request) {
        Queue queue = queueRepository.findById(request.getQueueId())
                .orElseThrow(() -> new IllegalArgumentException("Queue not found: " + request.getQueueId()));

        int maxAttempts = queue.getRetryPolicy() != null ? queue.getRetryPolicy().getMaxAttempts() : 3;

        LocalDateTime scheduledAt = LocalDateTime.now();
        JobStatus status = JobStatus.QUEUED;

        if (request.getType() == JobType.DELAYED) {
            int delay = request.getDelaySeconds() != null ? request.getDelaySeconds() : 0;
            scheduledAt = LocalDateTime.now().plusSeconds(delay);
            status = JobStatus.SCHEDULED;
        } else if (request.getType() == JobType.SCHEDULED) {
            scheduledAt = request.getScheduledAt() != null ? request.getScheduledAt() : LocalDateTime.now();
            if (scheduledAt.isAfter(LocalDateTime.now())) {
                status = JobStatus.SCHEDULED;
            }
        }

        Job parentJob = null;
        if (request.getParentJobId() != null) {
            parentJob = jobRepository.findById(request.getParentJobId())
                    .orElseThrow(() -> new IllegalArgumentException("Parent job not found: " + request.getParentJobId()));
        }

        Job job = Job.builder()
                .queue(queue)
                .parentJob(parentJob)
                .type(request.getType())
                .status(status)
                .payload(request.getPayload())
                .priority(request.getPriority() != null ? request.getPriority() : queue.getPriority())
                .attemptCount(0)
                .maxAttempts(maxAttempts)
                .scheduledAt(scheduledAt)
                .build();

        Job savedJob = jobRepository.save(job);
        logEvent(savedJob, "INFO", "Job created with type " + savedJob.getType() + " and status " + savedJob.getStatus());

        // If batch job, create all child jobs
        if (request.getType() == JobType.BATCH && request.getChildJobs() != null) {
            List<Job> children = new ArrayList<>();
            for (JobRequest childReq : request.getChildJobs()) {
                Job child = Job.builder()
                        .queue(queue)
                        .parentJob(savedJob)
                        .type(childReq.getType())
                        .status(JobStatus.QUEUED)
                        .payload(childReq.getPayload())
                        .priority(childReq.getPriority() != null ? childReq.getPriority() : savedJob.getPriority())
                        .attemptCount(0)
                        .maxAttempts(maxAttempts)
                        .scheduledAt(LocalDateTime.now())
                        .build();
                children.add(child);
            }
            jobRepository.saveAll(children);
            logEvent(savedJob, "INFO", "Batch parent initialized with " + children.size() + " child jobs");
            
            // Parent batch job itself is set to SCHEDULED or QUEUED, but won't be claimed directly by workers
            // We set status to RUNNING once execution of child jobs starts, or BATCH remains QUEUED.
            // Let's set parent status to QUEUED; workers will ignore BATCH type jobs when claiming.
            savedJob.setStatus(JobStatus.QUEUED);
            jobRepository.save(savedJob);
        }

        JobResponse response = mapToResponse(savedJob);
        jobBroadcastService.broadcastJobUpdate(response);
        dashboardBroadcastService.broadcastDashboardMetrics(queue.getProject().getId());
        eventBroadcastService.broadcastEvent("Job " + response.getId() + " created", "JOB");
        return response;
    }

    @Transactional
    public JobResponse cancelJob(Long jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        if (job.getStatus() == JobStatus.COMPLETED || job.getStatus() == JobStatus.DEAD_LETTER) {
            throw new IllegalStateException("Cannot cancel a completed or dead-lettered job");
        }

        job.setStatus(JobStatus.FAILED);
        job.setCompletedAt(LocalDateTime.now());
        jobRepository.save(job);
        logEvent(job, "WARN", "Job manually cancelled");

        JobResponse response = mapToResponse(job);
        jobBroadcastService.broadcastJobUpdate(response);
        dashboardBroadcastService.broadcastDashboardMetrics(job.getQueue().getProject().getId());
        eventBroadcastService.broadcastEvent("Job " + response.getId() + " cancelled", "JOB");
        return response;
    }

    @Transactional
    public JobResponse retryJob(Long jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));

        // Delete from DLQ if exists
        dlqRepository.findByJobId(jobId).ifPresent(dlqRepository::delete);

        job.setStatus(JobStatus.QUEUED);
        job.setAttemptCount(0);
        job.setScheduledAt(LocalDateTime.now());
        job.setStartedAt(null);
        job.setCompletedAt(null);
        job.setWorkerId(null);

        Job retriedJob = jobRepository.save(job);
        logEvent(retriedJob, "INFO", "Job manually rescheduled for retry");

        JobResponse response = mapToResponse(retriedJob);
        jobBroadcastService.broadcastJobUpdate(response);
        dashboardBroadcastService.broadcastDashboardMetrics(job.getQueue().getProject().getId());
        eventBroadcastService.broadcastEvent("Job " + response.getId() + " retried", "JOB");
        return response;
    }

    @Transactional
    public void logEvent(Job job, String level, String message) {
        JobLog jobLog = JobLog.builder()
                .job(job)
                .logLevel(level)
                .message(message)
                .build();
        jobLogRepository.save(jobLog);
        log.info("[Job ID: {}] [{}] - {}", job.getId(), level, message);
    }

    @Transactional(readOnly = true)
    public Page<JobResponse> listJobs(Specification<Job> spec, Pageable pageable) {
        return jobRepository.findAll(spec, pageable).map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public JobResponse getJobById(Long jobId) {
        Job job = jobRepository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("Job not found: " + jobId));
        return mapToResponse(job);
    }

    @Transactional(readOnly = true)
    public List<JobLog> getJobLogs(Long jobId) {
        return jobLogRepository.findByJobIdOrderByCreatedAtAsc(jobId);
    }

    public JobResponse mapToResponse(Job job) {
        Long parentId = job.getParentJob() != null ? job.getParentJob().getId() : null;

        JobResponse.JobResponseBuilder builder = JobResponse.builder()
                .id(job.getId())
                .queueId(job.getQueue().getId())
                .queueName(job.getQueue().getName())
                .parentJobId(parentId)
                .type(job.getType())
                .status(job.getStatus())
                .payload(job.getPayload())
                .priority(job.getPriority())
                .attemptCount(job.getAttemptCount())
                .maxAttempts(job.getMaxAttempts())
                .workerId(job.getWorkerId())
                .scheduledAt(job.getScheduledAt())
                .startedAt(job.getStartedAt())
                .completedAt(job.getCompletedAt())
                .createdAt(job.getCreatedAt())
                .updatedAt(job.getUpdatedAt());

        if (job.getType() == JobType.BATCH) {
            List<Job> children = jobRepository.findByParentJobId(job.getId());
            long total = children.size();
            long completed = children.stream().filter(c -> c.getStatus() == JobStatus.COMPLETED).count();
            long failed = children.stream().filter(c -> c.getStatus() == JobStatus.DEAD_LETTER || c.getStatus() == JobStatus.FAILED).count();
            builder.childJobCount(total)
                   .completedChildCount(completed)
                   .failedChildCount(failed);
        }

        return builder.build();
    }
}
