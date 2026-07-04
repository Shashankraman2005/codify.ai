package ai.coditiy.scheduler.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class DashboardMetricsResponse {
    private long queuedJobs;
    private long runningJobs;
    private long completedToday;
    private long failedToday;
    private long totalRetries;
    private long workersOnline;
    private long workersOffline;
    private long schedulesActive;
    private double avgExecutionTimeMs;
}
