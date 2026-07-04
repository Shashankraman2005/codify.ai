package ai.coditiy.scheduler.dto;

import ai.coditiy.scheduler.model.RetryStrategy;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class RetryPolicyRequest {
    @NotBlank
    private String name;

    @NotNull
    private RetryStrategy strategyType;

    @NotNull
    @Min(0)
    private Integer baseDelaySeconds;

    @NotNull
    @Min(0)
    private Integer maxDelaySeconds;

    @NotNull
    @Min(1)
    private Integer maxAttempts;
}
