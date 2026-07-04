package ai.coditiy.scheduler.service;

import ai.coditiy.scheduler.dto.QueueRequest;
import ai.coditiy.scheduler.dto.QueueResponse;
import ai.coditiy.scheduler.dto.QueueStats;
import ai.coditiy.scheduler.model.Project;
import ai.coditiy.scheduler.model.Queue;
import ai.coditiy.scheduler.model.RetryPolicy;
import ai.coditiy.scheduler.repository.JobRepository;
import ai.coditiy.scheduler.repository.ProjectRepository;
import ai.coditiy.scheduler.repository.QueueRepository;
import ai.coditiy.scheduler.repository.RetryPolicyRepository;
import ai.coditiy.scheduler.service.ws.QueueBroadcastService;
import ai.coditiy.scheduler.service.ws.DashboardBroadcastService;
import ai.coditiy.scheduler.service.ws.EventBroadcastService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class QueueService {

    private final QueueRepository queueRepository;
    private final ProjectRepository projectRepository;
    private final RetryPolicyRepository retryPolicyRepository;
    private final JobRepository jobRepository;
    private final QueueBroadcastService queueBroadcastService;
    private final DashboardBroadcastService dashboardBroadcastService;
    private final EventBroadcastService eventBroadcastService;

    @Transactional(readOnly = true)
    public List<QueueResponse> getQueuesForProject(Long projectId) {
        List<Queue> queues = queueRepository.findByProjectId(projectId);
        return queues.stream().map(this::mapToResponse).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public QueueResponse getQueueById(Long queueId) {
        Queue queue = queueRepository.findById(queueId)
                .orElseThrow(() -> new IllegalArgumentException("Queue not found: " + queueId));
        return mapToResponse(queue);
    }

    @Transactional
    public QueueResponse createQueue(QueueRequest request) {
        Project project = projectRepository.findById(request.getProjectId())
                .orElseThrow(() -> new IllegalArgumentException("Project not found: " + request.getProjectId()));

        RetryPolicy retryPolicy = null;
        if (request.getRetryPolicyId() != null) {
            retryPolicy = retryPolicyRepository.findById(request.getRetryPolicyId())
                    .orElseThrow(() -> new IllegalArgumentException("Retry Policy not found: " + request.getRetryPolicyId()));
        }

        Queue queue = Queue.builder()
                .name(request.getName())
                .project(project)
                .priority(request.getPriority())
                .concurrencyLimit(request.getConcurrencyLimit())
                .retryPolicy(retryPolicy)
                .isPaused(request.getIsPaused() != null && request.getIsPaused())
                .build();

        Queue savedQueue = queueRepository.save(queue);
        QueueResponse response = mapToResponse(savedQueue);
        queueBroadcastService.broadcastQueueUpdate(response);
        dashboardBroadcastService.broadcastDashboardMetrics(savedQueue.getProject().getId());
        eventBroadcastService.broadcastEvent("Queue " + savedQueue.getName() + " created", "QUEUE");
        
        return response;
    }

    @Transactional
    public QueueResponse updateQueue(Long id, QueueRequest request) {
        Queue queue = queueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Queue not found: " + id));

        RetryPolicy retryPolicy = null;
        if (request.getRetryPolicyId() != null) {
            retryPolicy = retryPolicyRepository.findById(request.getRetryPolicyId())
                    .orElseThrow(() -> new IllegalArgumentException("Retry Policy not found: " + request.getRetryPolicyId()));
        }

        queue.setName(request.getName());
        queue.setPriority(request.getPriority());
        queue.setConcurrencyLimit(request.getConcurrencyLimit());
        queue.setRetryPolicy(retryPolicy);
        if (request.getIsPaused() != null) {
            queue.setIsPaused(request.getIsPaused());
        }

        Queue savedQueue = queueRepository.save(queue);
        QueueResponse response = mapToResponse(savedQueue);
        queueBroadcastService.broadcastQueueUpdate(response);
        dashboardBroadcastService.broadcastDashboardMetrics(savedQueue.getProject().getId());
        eventBroadcastService.broadcastEvent("Queue " + savedQueue.getName() + " updated", "QUEUE");

        return response;
    }

    @Transactional
    public QueueResponse setPauseState(Long id, boolean isPaused) {
        Queue queue = queueRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Queue not found: " + id));
        queue.setIsPaused(isPaused);
        Queue savedQueue = queueRepository.save(queue);
        QueueResponse response = mapToResponse(savedQueue);
        queueBroadcastService.broadcastQueueUpdate(response);
        dashboardBroadcastService.broadcastDashboardMetrics(savedQueue.getProject().getId());
        eventBroadcastService.broadcastEvent("Queue " + savedQueue.getName() + " updated", "QUEUE");
        
        return response;
    }

    @Transactional(readOnly = true)
    public QueueStats getQueueStats(Long queueId) {
        long queued = jobRepository.countByQueueIdAndStatus(queueId, ai.coditiy.scheduler.model.JobStatus.QUEUED);
        long running = jobRepository.countByQueueIdAndStatus(queueId, ai.coditiy.scheduler.model.JobStatus.RUNNING);
        long failed = jobRepository.countByQueueIdAndStatus(queueId, ai.coditiy.scheduler.model.JobStatus.FAILED);
        long completed = jobRepository.countByQueueIdAndStatus(queueId, ai.coditiy.scheduler.model.JobStatus.COMPLETED);
        
        Double avgExec = jobRepository.getAverageExecutionTimeSeconds(queueId);
        double avgTime = avgExec != null ? avgExec : 0.0;

        return new QueueStats(queued, running, failed, completed, avgTime);
    }

    private QueueResponse mapToResponse(Queue queue) {
        String rpName = queue.getRetryPolicy() != null ? queue.getRetryPolicy().getName() : null;
        Long rpId = queue.getRetryPolicy() != null ? queue.getRetryPolicy().getId() : null;

        return QueueResponse.builder()
                .id(queue.getId())
                .name(queue.getName())
                .projectId(queue.getProject().getId())
                .projectName(queue.getProject().getName())
                .priority(queue.getPriority())
                .concurrencyLimit(queue.getConcurrencyLimit())
                .retryPolicyId(rpId)
                .retryPolicyName(rpName)
                .isPaused(queue.getIsPaused())
                .stats(getQueueStats(queue.getId()))
                .createdAt(queue.getCreatedAt())
                .updatedAt(queue.getUpdatedAt())
                .build();
    }
}
