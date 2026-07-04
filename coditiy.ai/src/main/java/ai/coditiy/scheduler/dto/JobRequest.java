package ai.coditiy.scheduler.dto;

import ai.coditiy.scheduler.model.JobType;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class JobRequest {
    @NotNull
    private Long queueId;

    private Long parentJobId;

    @NotNull
    private JobType type;

    private String payload;

    private Integer priority;

    // Delayed jobs
    private Integer delaySeconds;

    // Scheduled jobs
    private LocalDateTime scheduledAt;

    // Recurring (cron) jobs
    private String cronExpression;

    // Batch jobs - list of child job requests
    private List<JobRequest> childJobs;
}
