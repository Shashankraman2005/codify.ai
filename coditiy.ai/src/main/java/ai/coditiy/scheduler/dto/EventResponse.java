package ai.coditiy.scheduler.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class EventResponse {
    private String id;
    private String message;
    private String type; // JOB, QUEUE, WORKER, SCHEDULE
    private LocalDateTime timestamp;
}
