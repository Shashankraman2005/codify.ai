package ai.coditiy.scheduler.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueStats {
    private long queuedCount;
    private long runningCount;
    private long failedCount;
    private long completedCount;
    private double averageExecutionTimeSeconds;
}
