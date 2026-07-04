package ai.coditiy.scheduler.dto;

import ai.coditiy.scheduler.model.ScheduleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class ScheduleRequest {
    
    @NotNull(message = "Queue ID is required")
    private Long queueId;
    
    @NotBlank(message = "Schedule name is required")
    private String name;
    
    @NotNull(message = "Schedule type is required")
    private ScheduleType scheduleType;
    
    private String cronExpression;
    private Integer intervalSeconds;
    private LocalDateTime scheduledAt;
    
    private String payload;
}
