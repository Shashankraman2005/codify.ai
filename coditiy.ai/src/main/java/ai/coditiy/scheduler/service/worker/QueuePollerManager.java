package ai.coditiy.scheduler.service.worker;

import ai.coditiy.scheduler.model.Queue;
import ai.coditiy.scheduler.repository.QueueRepository;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

@Service
@Profile("worker")
@RequiredArgsConstructor
@Slf4j
public class QueuePollerManager {

    private final QueueRepository queueRepository;
    private final JobClaimService claimService;
    private final JobExecutorService executorService;

    @Value("${scheduler.worker.id}")
    private String workerId;

    @Value("${scheduler.worker.poll-interval-ms:1000}")
    private long pollIntervalMs;

    private final Map<Long, QueuePoller> activePollers = new ConcurrentHashMap<>();
    private final Map<Long, ThreadPoolExecutor> activeExecutors = new ConcurrentHashMap<>();
    private final Map<Long, Future<?>> pollerFutures = new ConcurrentHashMap<>();
    private final ExecutorService pollerExecutorService = Executors.newCachedThreadPool();

    @Scheduled(fixedDelay = 5000)
    public void syncPollers() {
        log.debug("Synchronizing queue pollers with database...");
        
        // Fetch all active, unpaused queues
        java.util.List<Queue> queues = queueRepository.findAll();
        
        // Track queues active in this run
        java.util.Set<Long> dbActiveQueueIds = new java.util.HashSet<>();

        for (Queue queue : queues) {
            if (!queue.getIsPaused()) {
                dbActiveQueueIds.add(queue.getId());
                syncQueue(queue);
            }
        }

        // Clean up queues that are now paused or deleted
        for (Long queueId : activePollers.keySet()) {
            if (!dbActiveQueueIds.contains(queueId)) {
                stopPoller(queueId);
            }
        }
    }

    private void syncQueue(Queue queue) {
        Long queueId = queue.getId();
        int limit = queue.getConcurrencyLimit();

        if (!activePollers.containsKey(queueId)) {
            // Initialize new executor and poller
            log.info("Initializing worker pool for queue '{}' with limit {}", queue.getName(), limit);
            
            ThreadPoolExecutor executor = new ThreadPoolExecutor(
                    limit,
                    limit,
                    60L, TimeUnit.SECONDS,
                    new SynchronousQueue<>()
            );
            
            QueuePoller poller = new QueuePoller(queueId, executor);
            activeExecutors.put(queueId, executor);
            activePollers.put(queueId, poller);
            
            Future<?> future = pollerExecutorService.submit(poller);
            pollerFutures.put(queueId, future);
        } else {
            // Check if limit has changed
            ThreadPoolExecutor executor = activeExecutors.get(queueId);
            if (executor != null && executor.getMaximumPoolSize() != limit) {
                log.info("Updating queue '{}' concurrency limit from {} to {}", queue.getName(), executor.getMaximumPoolSize(), limit);
                executor.setCorePoolSize(limit);
                executor.setMaximumPoolSize(limit);
            }
        }
    }

    private void stopPoller(Long queueId) {
        log.info("Stopping poller for queue ID: {}", queueId);
        
        QueuePoller poller = activePollers.remove(queueId);
        if (poller != null) {
            poller.stop();
        }

        Future<?> future = pollerFutures.remove(queueId);
        if (future != null) {
            future.cancel(true);
        }

        ThreadPoolExecutor executor = activeExecutors.remove(queueId);
        if (executor != null) {
            executor.shutdown();
            try {
                if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
            }
        }
    }

    @PreDestroy
    public void stopAll() {
        log.info("SIGTERM/Shutdown signal received. Gracefully stopping all queue pollers and executing threads...");
        
        // 1. Signal all pollers to stop claiming
        for (QueuePoller poller : activePollers.values()) {
            poller.stop();
        }
        for (Future<?> future : pollerFutures.values()) {
            future.cancel(true);
        }

        // 2. Shut down executors
        for (ThreadPoolExecutor executor : activeExecutors.values()) {
            executor.shutdown();
        }

        // 3. Await in-flight executions
        for (Map.Entry<Long, ThreadPoolExecutor> entry : activeExecutors.entrySet()) {
            Long queueId = entry.getKey();
            ThreadPoolExecutor executor = entry.getValue();
            try {
                log.info("Waiting for queue ID {} tasks to complete...", queueId);
                if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
                    log.warn("Queue ID {} tasks did not finish within graceful period. Forcing shutdown.", queueId);
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                executor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        
        pollerExecutorService.shutdownNow();
        log.info("Graceful worker shutdown complete.");
    }

    private class QueuePoller implements Runnable {
        private final Long queueId;
        private final ThreadPoolExecutor executor;
        private volatile boolean running = true;

        public QueuePoller(Long queueId, ThreadPoolExecutor executor) {
            this.queueId = queueId;
            this.executor = executor;
        }

        public void stop() {
            this.running = false;
        }

        @Override
        public void run() {
            log.info("Poller loop started for queue ID: {}", queueId);
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    int activeCount = executor.getActiveCount();
                    int maxPoolSize = executor.getMaximumPoolSize();

                    if (activeCount < maxPoolSize) {
                        // We have capacity: poll and claim a job
                        Optional<ai.coditiy.scheduler.model.Job> jobOpt = claimService.claimJob(queueId, workerId);
                        if (jobOpt.isPresent()) {
                            ai.coditiy.scheduler.model.Job job = jobOpt.get();
                            
                            // Submit execution task to executor
                            executor.submit(() -> executorService.executeJob(job.getId(), workerId));
                        } else {
                            // No jobs: sleep poll interval
                            Thread.sleep(pollIntervalMs);
                        }
                    } else {
                        // Saturated: wait briefly
                        Thread.sleep(200);
                    }
                } catch (InterruptedException e) {
                    log.info("Poller loop for queue ID {} interrupted.", queueId);
                    break;
                } catch (Exception e) {
                    log.error("Exception in poller thread for queue ID {}", queueId, e);
                    try {
                        Thread.sleep(pollIntervalMs);
                    } catch (InterruptedException ex) {
                        break;
                    }
                }
            }
            log.info("Poller loop ended for queue ID: {}", queueId);
        }
    }
}
