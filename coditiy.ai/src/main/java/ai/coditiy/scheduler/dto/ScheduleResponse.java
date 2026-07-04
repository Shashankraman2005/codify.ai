package ai.coditiy.scheduler.dto;

import ai.coditiy.scheduler.model.ScheduleStatus;
import ai.coditiy.scheduler.model.ScheduleType;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ScheduleResponse {
    private Long id;
    private Long projectId;
    private Long queueId;
    private String name;
    private ScheduleType scheduleType;
    private String cronExpression;
    private Integer intervalSeconds;
    private LocalDateTime scheduledAt;
    private String payload;
    private ScheduleStatus status;
    private LocalDateTime nextFireTime;
    private LocalDateTime previousFireTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
