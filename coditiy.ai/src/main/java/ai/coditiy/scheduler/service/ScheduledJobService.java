package ai.coditiy.scheduler.service;

import ai.coditiy.scheduler.dto.ScheduledJobRequest;
import ai.coditiy.scheduler.model.Project;
import ai.coditiy.scheduler.model.Queue;
import ai.coditiy.scheduler.model.ScheduledJob;
import ai.coditiy.scheduler.repository.ProjectRepository;
import ai.coditiy.scheduler.repository.QueueRepository;
import ai.coditiy.scheduler.repository.ScheduledJobRepository;
import ai.coditiy.scheduler.service.job.QuartzJobLauncher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledJobService {

    private final ScheduledJobRepository scheduledJobRepository;
    private final ProjectRepository projectRepository;
    private final QueueRepository queueRepository;
    private final org.quartz.Scheduler quartzScheduler;

    @EventListener(ApplicationReadyEvent.class)
    public void initSchedules() {
        log.info("Initializing active cron schedules in Quartz engine...");
        List<ScheduledJob> activeJobs = scheduledJobRepository.findByIsActiveTrue();
        for (ScheduledJob job : activeJobs) {
            try {
                syncWithQuartz(job);
            } catch (Exception e) {
                log.error("Failed to schedule cron job '{}' at startup", job.getName(), e);
            }
        }
    }

    @Transactional(readOnly = true)
    public List<ScheduledJob> getScheduledJobsForProject(Long projectId) {
        return scheduledJobRepository.findByProjectId(projectId);
    }

    @Transactional
    public ScheduledJob createScheduledJob(ScheduledJobRequest request) {
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + request.getProjectId()));

        Queue queue = queueRepository.findById(request.getQueueId())
                .orElseThrow(() -> new IllegalArgumentException("Queue not found: " + request.getQueueId()));

        ScheduledJob scheduledJob = ScheduledJob.builder()
                .name(request.getName())
                .project(project)
                .queue(queue)
                .cronExpression(request.getCronExpression())
                .jobPayload(request.getJobPayload())
                .priority(request.getPriority())
                .isActive(request.getIsActive() == null || request.getIsActive())
                .build();

        ScheduledJob saved = scheduledJobRepository.save(scheduledJob);
        
        try {
            syncWithQuartz(saved);
        } catch (SchedulerException e) {
            log.error("Failed to sync cron job '{}' to Quartz", saved.getName(), e);
            throw new RuntimeException("Quartz scheduling failed: " + e.getMessage(), e);
        }

        return saved;
    }

    @Transactional
    public ScheduledJob updateScheduledJob(Long id, ScheduledJobRequest request) {
        ScheduledJob job = scheduledJobRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Scheduled job definition not found: " + id));

        Queue queue = queueRepository.findById(request.getQueueId())
                .orElseThrow(() -> new IllegalArgumentException("Queue not found: " + request.getQueueId()));

        job.setName(request.getName());
        job.setQueue(queue);
        job.setCronExpression(request.getCronExpression());
        job.setJobPayload(request.getJobPayload());
        job.setPriority(request.getPriority());
        if (request.getIsActive() != null) {
            job.setIsActive(request.getIsActive());
        }

        ScheduledJob saved = scheduledJobRepository.save(job);
        
        try {
            syncWithQuartz(saved);
        } catch (SchedulerException e) {
            log.error("Failed to sync cron job '{}' to Quartz", saved.getName(), e);
            throw new RuntimeException("Quartz scheduling failed: " + e.getMessage(), e);
        }

        return saved;
    }

    @Transactional
    public void deleteScheduledJob(Long id) {
        ScheduledJob job = scheduledJobRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Scheduled job definition not found: " + id));

        scheduledJobRepository.delete(job);
        
        try {
            JobKey jobKey = new JobKey("cron_" + id, "cron_group");
            if (quartzScheduler.checkExists(jobKey)) {
                quartzScheduler.deleteJob(jobKey);
            }
        } catch (SchedulerException e) {
            log.error("Failed to remove cron job ID {} from Quartz", id, e);
        }
    }

    private void syncWithQuartz(ScheduledJob job) throws SchedulerException {
        JobKey jobKey = new JobKey("cron_" + job.getId(), "cron_group");
        
        // Remove existing trigger/job definition if exists
        if (quartzScheduler.checkExists(jobKey)) {
            quartzScheduler.deleteJob(jobKey);
        }

        if (job.getIsActive()) {
            JobDetail jobDetail = JobBuilder.newJob(QuartzJobLauncher.class)
                    .withIdentity(jobKey)
                    .usingJobData("scheduledJobId", job.getId())
                    .build();

            Trigger trigger = TriggerBuilder.newTrigger()
                    .withIdentity("trigger_" + job.getId(), "cron_group")
                    .withSchedule(CronScheduleBuilder.cronSchedule(job.getCronExpression())
                            .withMisfireHandlingInstructionDoNothing()) // skip misfires cleanly
                    .build();

            quartzScheduler.scheduleJob(jobDetail, trigger);

            // Compute and record next fire time
            Date nextFireTime = trigger.getNextFireTime();
            if (nextFireTime != null) {
                job.setNextRunAt(LocalDateTime.ofInstant(nextFireTime.toInstant(), ZoneId.systemDefault()));
                scheduledJobRepository.save(job);
            }

            log.info("Scheduled cron job '{}' (ID {}) with Quartz cron: {}", job.getName(), job.getId(), job.getCronExpression());
        } else {
            log.info("Unscheduled cron job '{}' (ID {}) as it is marked inactive", job.getName(), job.getId());
        }
    }
}
