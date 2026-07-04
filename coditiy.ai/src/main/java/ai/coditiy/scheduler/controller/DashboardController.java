package ai.coditiy.scheduler.controller;

import ai.coditiy.scheduler.model.JobStatus;
import ai.coditiy.scheduler.model.Queue;
import ai.coditiy.scheduler.model.Worker;
import ai.coditiy.scheduler.repository.JobRepository;
import ai.coditiy.scheduler.repository.QueueRepository;
import ai.coditiy.scheduler.repository.WorkerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DashboardController {

    private final WorkerRepository workerRepository;
    private final JobRepository jobRepository;
    private final QueueRepository queueRepository;

    @GetMapping("/workers")
    public ResponseEntity<List<Worker>> getWorkers() {
        return ResponseEntity.ok(workerRepository.findAll());
    }

    @GetMapping("/dashboard/stats")
    @PreAuthorize("@securityService.hasProjectRole(#projectId, 'ADMIN', 'MEMBER', 'VIEWER')")
    public ResponseEntity<?> getDashboardStats(@RequestParam Long projectId) {
        List<Queue> queues = queueRepository.findByProjectId(projectId);
        
        long totalQueues = queues.size();
        long queuedCount = 0;
        long runningCount = 0;
        long failedCount = 0;
        long completedCount = 0;

        for (Queue queue : queues) {
            queuedCount += jobRepository.countByQueueIdAndStatus(queue.getId(), JobStatus.QUEUED);
            runningCount += jobRepository.countByQueueIdAndStatus(queue.getId(), JobStatus.RUNNING);
            failedCount += jobRepository.countByQueueIdAndStatus(queue.getId(), JobStatus.FAILED)
                         + jobRepository.countByQueueIdAndStatus(queue.getId(), JobStatus.DEAD_LETTER);
            completedCount += jobRepository.countByQueueIdAndStatus(queue.getId(), JobStatus.COMPLETED);
        }

        // Fetch hourly throughput for the last 24 hours
        List<Object[]> completedRaw = jobRepository.getHourlyThroughputCompleted(projectId);
        List<Object[]> failedRaw = jobRepository.getHourlyThroughputFailed(projectId);

        Map<Integer, Long> completedMap = new HashMap<>();
        Map<Integer, Long> failedMap = new HashMap<>();

        for (Object[] row : completedRaw) {
            completedMap.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }
        for (Object[] row : failedRaw) {
            failedMap.put(((Number) row[0]).intValue(), ((Number) row[1]).longValue());
        }

        // Build a structured 24-hour timeline list for Recharts (hours 0 to 23)
        List<Map<String, Object>> chartData = new ArrayList<>();
        int currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);

        for (int i = 23; i >= 0; i--) {
            int targetHour = (currentHour - i + 24) % 24;
            Map<String, Object> dataPoint = new HashMap<>();
            dataPoint.put("hour", String.format("%02d:00", targetHour));
            dataPoint.put("completed", completedMap.getOrDefault(targetHour, 0L));
            dataPoint.put("failed", failedMap.getOrDefault(targetHour, 0L));
            chartData.add(dataPoint);
        }

        Map<String, Object> stats = new HashMap<>();
        stats.put("totalQueues", totalQueues);
        stats.put("queuedCount", queuedCount);
        stats.put("runningCount", runningCount);
        stats.put("failedCount", failedCount);
        stats.put("completedCount", completedCount);
        stats.put("totalJobs", queuedCount + runningCount + failedCount + completedCount);
        stats.put("throughputChart", chartData);

        return ResponseEntity.ok(stats);
    }
}
