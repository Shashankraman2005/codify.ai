package ai.coditiy.scheduler.service.schedule;

import ai.coditiy.scheduler.dto.ScheduleRequest;
import ai.coditiy.scheduler.dto.ScheduleResponse;
import ai.coditiy.scheduler.model.Queue;
import ai.coditiy.scheduler.model.Schedule;
import ai.coditiy.scheduler.model.ScheduleStatus;
import ai.coditiy.scheduler.model.ScheduleType;
import ai.coditiy.scheduler.repository.QueueRepository;
import ai.coditiy.scheduler.repository.ScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.quartz.*;
import org.springframework.stereotype.Service;
import ai.coditiy.scheduler.service.ws.ScheduleBroadcastService;
import ai.coditiy.scheduler.service.ws.DashboardBroadcastService;
import ai.coditiy.scheduler.service.ws.EventBroadcastService;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleService {

    private final ScheduleRepository scheduleRepository;
    private final QueueRepository queueRepository;
    private final Scheduler scheduler;
    private final ScheduleBroadcastService scheduleBroadcastService;
    private final DashboardBroadcastService dashboardBroadcastService;
    private final EventBroadcastService eventBroadcastService;
    
    private static final String SCHEDULE_GROUP = "DEFAULT_SCHEDULE_GROUP";

    @Transactional
    public ScheduleResponse createSchedule(Long projectId, ScheduleRequest request) {
        Queue queue = queueRepository.findById(request.getQueueId())
                .orElseThrow(() -> new IllegalArgumentException("Queue not found: " + request.getQueueId()));

        if (!queue.getProject().getId().equals(projectId)) {
            throw new IllegalArgumentException("Queue does not belong to project " + projectId);
        }

        Schedule schedule = Schedule.builder()
                .projectId(projectId)
                .queueId(queue.getId())
                .name(request.getName())
                .scheduleType(request.getScheduleType())
                .cronExpression(request.getCronExpression())
                .intervalSeconds(request.getIntervalSeconds())
                .scheduledAt(request.getScheduledAt())
                .payload(request.getPayload())
                .status(ScheduleStatus.ACTIVE)
                .build();

        schedule = scheduleRepository.save(schedule);

        try {
            scheduleQuartzJob(schedule);
        } catch (SchedulerException e) {
            log.error("Failed to schedule quartz job", e);
            throw new RuntimeException("Failed to schedule job in Quartz", e);
        }

        ScheduleResponse response = mapToResponse(schedule);
        scheduleBroadcastService.broadcastScheduleUpdate(response);
        dashboardBroadcastService.broadcastDashboardMetrics(projectId);
        eventBroadcastService.broadcastEvent("Schedule " + schedule.getName() + " created", "SCHEDULE");

        return response;
    }

    private void scheduleQuartzJob(Schedule schedule) throws SchedulerException {
        JobDetail jobDetail = JobBuilder.newJob(QuartzEnqueueJob.class)
                .withIdentity(schedule.getId().toString(), SCHEDULE_GROUP)
                .usingJobData("scheduleId", schedule.getId())
                .storeDurably()
                .build();

        TriggerBuilder<Trigger> triggerBuilder = TriggerBuilder.newTrigger()
                .withIdentity(schedule.getId().toString(), SCHEDULE_GROUP)
                .forJob(jobDetail);

        switch (schedule.getScheduleType()) {
            case ONE_TIME:
            case DELAYED:
                LocalDateTime fireTime = schedule.getScheduledAt() != null ? schedule.getScheduledAt() : LocalDateTime.now();
                if (fireTime.isBefore(LocalDateTime.now())) {
                    fireTime = LocalDateTime.now();
                }
                triggerBuilder.startAt(Date.from(fireTime.atZone(ZoneId.systemDefault()).toInstant()));
                triggerBuilder.withSchedule(SimpleScheduleBuilder.simpleSchedule().withMisfireHandlingInstructionFireNow());
                break;
            case CRON:
                triggerBuilder.withSchedule(CronScheduleBuilder.cronSchedule(schedule.getCronExpression()));
                break;
            case FIXED_INTERVAL:
                triggerBuilder.withSchedule(SimpleScheduleBuilder.simpleSchedule()
                        .withIntervalInSeconds(schedule.getIntervalSeconds())
                        .repeatForever());
                break;
        }

        Trigger trigger = triggerBuilder.build();
        scheduler.scheduleJob(jobDetail, trigger);
        log.info("Scheduled Quartz job for schedule ID: {}", schedule.getId());
    }

    @Transactional(readOnly = true)
    public List<ScheduleResponse> getSchedulesForProject(Long projectId) {
        return scheduleRepository.findByProjectId(projectId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public ScheduleResponse getSchedule(Long id) {
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + id));
        return mapToResponse(schedule);
    }

    @Transactional
    public void deleteSchedule(Long id) {
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + id));
        try {
            scheduler.deleteJob(JobKey.jobKey(id.toString(), SCHEDULE_GROUP));
            scheduleRepository.delete(schedule);
            
            dashboardBroadcastService.broadcastDashboardMetrics(schedule.getProjectId());
            eventBroadcastService.broadcastEvent("Schedule " + schedule.getName() + " deleted", "SCHEDULE");
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to delete Quartz job", e);
        }
    }

    @Transactional
    public ScheduleResponse pauseSchedule(Long id) {
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + id));
        
        if (schedule.getStatus() == ScheduleStatus.COMPLETED) {
            throw new IllegalStateException("Cannot pause a completed schedule");
        }

        try {
            scheduler.pauseJob(JobKey.jobKey(id.toString(), SCHEDULE_GROUP));
            schedule.setStatus(ScheduleStatus.PAUSED);
            schedule = scheduleRepository.save(schedule);
            ScheduleResponse response = mapToResponse(schedule);
            scheduleBroadcastService.broadcastScheduleUpdate(response);
            eventBroadcastService.broadcastEvent("Schedule " + schedule.getName() + " paused", "SCHEDULE");
            return response;
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to pause Quartz job", e);
        }
    }

    @Transactional
    public ScheduleResponse resumeSchedule(Long id) {
        Schedule schedule = scheduleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Schedule not found: " + id));
        
        if (schedule.getStatus() == ScheduleStatus.COMPLETED) {
            throw new IllegalStateException("Cannot resume a completed schedule");
        }

        try {
            scheduler.resumeJob(JobKey.jobKey(id.toString(), SCHEDULE_GROUP));
            schedule.setStatus(ScheduleStatus.ACTIVE);
            schedule = scheduleRepository.save(schedule);
            ScheduleResponse response = mapToResponse(schedule);
            scheduleBroadcastService.broadcastScheduleUpdate(response);
            eventBroadcastService.broadcastEvent("Schedule " + schedule.getName() + " resumed", "SCHEDULE");
            return response;
        } catch (SchedulerException e) {
            throw new RuntimeException("Failed to resume Quartz job", e);
        }
    }

    @Transactional(readOnly = true)
    public void broadcastScheduleState(Long id) {
        scheduleRepository.findById(id).ifPresent(schedule -> {
            ScheduleResponse response = mapToResponse(schedule);
            scheduleBroadcastService.broadcastScheduleUpdate(response);
        });
    }

    private ScheduleResponse mapToResponse(Schedule schedule) {
        LocalDateTime nextFireTime = null;
        LocalDateTime prevFireTime = null;

        try {
            Trigger trigger = scheduler.getTrigger(TriggerKey.triggerKey(schedule.getId().toString(), SCHEDULE_GROUP));
            if (trigger != null) {
                Date next = trigger.getNextFireTime();
                Date prev = trigger.getPreviousFireTime();
                if (next != null) {
                    nextFireTime = next.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                }
                if (prev != null) {
                    prevFireTime = prev.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime();
                }
            }
        } catch (SchedulerException e) {
            log.warn("Failed to retrieve Quartz trigger for schedule {}", schedule.getId(), e);
        }

        return ScheduleResponse.builder()
                .id(schedule.getId())
                .projectId(schedule.getProjectId())
                .queueId(schedule.getQueueId())
                .name(schedule.getName())
                .scheduleType(schedule.getScheduleType())
                .cronExpression(schedule.getCronExpression())
                .intervalSeconds(schedule.getIntervalSeconds())
                .scheduledAt(schedule.getScheduledAt())
                .payload(schedule.getPayload())
                .status(schedule.getStatus())
                .nextFireTime(nextFireTime)
                .previousFireTime(prevFireTime)
                .createdAt(schedule.getCreatedAt())
                .updatedAt(schedule.getUpdatedAt())
                .build();
    }
}
