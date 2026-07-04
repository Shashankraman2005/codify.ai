package ai.coditiy.scheduler.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class QueueRequest {
    @NotBlank
    private String name;

    private Long projectId;

    @NotNull
    private Integer priority;

    @NotNull
    @Min(1)
    private Integer concurrencyLimit;

    private Long retryPolicyId;

    private Boolean isPaused;
}
