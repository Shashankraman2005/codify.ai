package ai.coditiy.scheduler.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class ScheduledJobRequest {
    @NotBlank
    private String name;

    @NotBlank
    private String cronExpression;

    private String jobPayload;

    @NotNull
    private Integer priority;

    @NotNull
    private Long projectId;

    @NotNull
    private Long queueId;

    private Boolean isActive;
}
