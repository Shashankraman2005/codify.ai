package ai.coditiy.scheduler.service.ws;

import ai.coditiy.scheduler.controller.DashboardController;
import ai.coditiy.scheduler.model.JobStatus;
import ai.coditiy.scheduler.model.Queue;
import ai.coditiy.scheduler.repository.JobRepository;
import ai.coditiy.scheduler.repository.QueueRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;
    private final JobRepository jobRepository;
    private final QueueRepository queueRepository;

    public void broadcastDashboardMetrics(Long projectId) {
        log.debug("Broadcasting dashboard metrics for Project ID: {}", projectId);
        
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
        stats.put("projectId", projectId);
        stats.put("totalQueues", totalQueues);
        stats.put("queuedCount", queuedCount);
        stats.put("runningCount", runningCount);
        stats.put("failedCount", failedCount);
        stats.put("completedCount", completedCount);
        stats.put("totalJobs", queuedCount + runningCount + failedCount + completedCount);
        stats.put("throughputChart", chartData);

        messagingTemplate.convertAndSend("/topic/dashboard", stats);
    }
}
