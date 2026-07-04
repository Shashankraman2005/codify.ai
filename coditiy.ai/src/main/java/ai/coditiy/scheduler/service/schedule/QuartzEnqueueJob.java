package ai.coditiy.scheduler.service.schedule;

import ai.coditiy.scheduler.dto.JobRequest;
import ai.coditiy.scheduler.model.JobType;
import ai.coditiy.scheduler.model.Schedule;
import ai.coditiy.scheduler.model.ScheduleStatus;
import ai.coditiy.scheduler.model.ScheduleType;
import ai.coditiy.scheduler.repository.ScheduleRepository;
import ai.coditiy.scheduler.service.JobService;
import ai.coditiy.scheduler.service.ws.ScheduleBroadcastService;
import ai.coditiy.scheduler.service.schedule.ScheduleService;
import lombok.extern.slf4j.Slf4j;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.quartz.QuartzJobBean;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class QuartzEnqueueJob extends QuartzJobBean {

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private JobService jobService;

    @Autowired
    @org.springframework.context.annotation.Lazy
    private ScheduleService scheduleService;

    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        Long scheduleId = context.getMergedJobDataMap().getLong("scheduleId");
        
        log.info("Quartz trigger fired for schedule ID: {}", scheduleId);

        Schedule schedule = scheduleRepository.findById(scheduleId).orElse(null);
        if (schedule == null) {
            log.warn("Schedule ID {} not found. It may have been deleted.", scheduleId);
            return;
        }

        if (schedule.getStatus() != ScheduleStatus.ACTIVE) {
            log.warn("Schedule ID {} fired but status is {}. Ignoring.", scheduleId, schedule.getStatus());
            return;
        }

        // Build Job Request to enqueue into the standard worker pool
        JobRequest req = new JobRequest();
        req.setQueueId(schedule.getQueueId());
        req.setType(JobType.IMMEDIATE);
        req.setPayload(schedule.getPayload());

        try {
            jobService.createJob(req);
            log.info("Successfully enqueued job for schedule ID: {}", scheduleId);
        } catch (Exception e) {
            log.error("Failed to enqueue job for schedule ID: {}", scheduleId, e);
            throw new JobExecutionException(e);
        }

        // If it's a one-time execution, mark the schedule as COMPLETED
        if (schedule.getScheduleType() == ScheduleType.ONE_TIME || schedule.getScheduleType() == ScheduleType.DELAYED) {
            schedule.setStatus(ScheduleStatus.COMPLETED);
            scheduleRepository.save(schedule);
            log.info("Marked one-time schedule ID: {} as COMPLETED", scheduleId);
        }

        // Broadcast the updated state (e.g., next fire time)
        if (scheduleService != null) {
            scheduleService.broadcastScheduleState(scheduleId);
        }
    }
}
