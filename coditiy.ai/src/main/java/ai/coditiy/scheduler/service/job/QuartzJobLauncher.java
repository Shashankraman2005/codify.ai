package ai.coditiy.scheduler.service.job;

import ai.coditiy.scheduler.dto.JobRequest;
import ai.coditiy.scheduler.model.JobType;
import ai.coditiy.scheduler.model.ScheduledJob;
import ai.coditiy.scheduler.repository.ScheduledJobRepository;
import ai.coditiy.scheduler.service.JobService;
import ai.coditiy.scheduler.service.worker.LockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.Job;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Component
@Slf4j
@RequiredArgsConstructor
public class QuartzJobLauncher implements Job {

    private final ScheduledJobRepository scheduledJobRepository;
    private final JobService jobService;
    private final LockService lockService;

    @Value("${scheduler.worker.id}")
    private String workerId;

    @Override
    @Transactional
    public void execute(JobExecutionContext context) throws JobExecutionException {
        Long scheduledJobId = context.getMergedJobDataMap().getLong("scheduledJobId");
        
        // Deduplicate runs across multiple scheduler nodes using a lease lock based on scheduled fire time
        long fireTimeMinutes = context.getScheduledFireTime().getTime() / 60000;
        String lockName = "CRON_FIRE_" + scheduledJobId + "_" + fireTimeMinutes;

        // Try to acquire lock for 120 seconds
        boolean isLeaderForRun = lockService.acquireLock(lockName, workerId, 120L);
        if (!isLeaderForRun) {
            log.info("Cron run for ScheduledJob ID {} already claimed by another node. Skipping.", scheduledJobId);
            return;
        }

        log.info("Executing scheduled cron job ID {} on worker {}", scheduledJobId, workerId);
        ScheduledJob scheduledJob = scheduledJobRepository.findById(scheduledJobId).orElse(null);
        if (scheduledJob == null) {
            log.warn("ScheduledJob ID {} not found. Skipping.", scheduledJobId);
            return;
        }

        if (!scheduledJob.getIsActive()) {
            log.info("ScheduledJob ID {} is inactive. Skipping.", scheduledJobId);
            return;
        }

        try {
            JobRequest request = new JobRequest();
            request.setQueueId(scheduledJob.getQueue().getId());
            request.setType(JobType.RECURRING);
            request.setPayload(scheduledJob.getJobPayload());
            request.setPriority(scheduledJob.getPriority());

            jobService.createJob(request);

            scheduledJob.setLastRunAt(LocalDateTime.now());
            
            Date nextFireTime = context.getTrigger().getNextFireTime();
            if (nextFireTime != null) {
                scheduledJob.setNextRunAt(LocalDateTime.ofInstant(nextFireTime.toInstant(), ZoneId.systemDefault()));
            }
            
            scheduledJobRepository.save(scheduledJob);
            log.info("Cron execution succeeded for '{}'", scheduledJob.getName());

        } catch (Exception e) {
            log.error("Failed to launch job instance for recurring job '{}'", scheduledJob.getName(), e);
            throw new JobExecutionException(e);
        }
    }
}
