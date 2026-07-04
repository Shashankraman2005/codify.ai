package ai.coditiy.scheduler.service.worker;

import ai.coditiy.scheduler.model.Worker;
import ai.coditiy.scheduler.model.WorkerHeartbeat;
import ai.coditiy.scheduler.model.WorkerStatus;
import ai.coditiy.scheduler.repository.WorkerHeartbeatRepository;
import ai.coditiy.scheduler.repository.WorkerRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ai.coditiy.scheduler.service.ws.WorkerBroadcastService;
import ai.coditiy.scheduler.service.ws.EventBroadcastService;
import java.net.InetAddress;
import java.time.LocalDateTime;

@Service
@Profile("worker")
@RequiredArgsConstructor
@Slf4j
public class WorkerHeartbeatService {

    private final WorkerRepository workerRepository;
    private final WorkerHeartbeatRepository heartbeatRepository;
    private final WorkerBroadcastService workerBroadcastService;
    private final EventBroadcastService eventBroadcastService;

    @Value("${scheduler.worker.id}")
    private String workerId;

    @PostConstruct
    @Transactional
    public void registerWorker() {
        String hostname = "unknown";
        try {
            hostname = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            log.warn("Could not resolve local hostname: {}", e.getMessage());
        }

        log.info("Registering worker node: ID={}, Hostname={}", workerId, hostname);
        Worker worker = Worker.builder()
                .workerId(workerId)
                .hostname(hostname)
                .status(WorkerStatus.ACTIVE)
                .lastHeartbeatAt(LocalDateTime.now())
                .build();
        workerRepository.save(worker);
        workerBroadcastService.broadcastWorkerUpdate(worker);
        eventBroadcastService.broadcastEvent("Worker " + workerId + " connected", "WORKER");
    }

    @Scheduled(fixedDelayString = "${scheduler.worker.heartbeat-interval-ms:10000}")
    @Transactional
    public void sendHeartbeat() {
        log.debug("Sending heartbeat for worker node: {}", workerId);
        workerRepository.findById(workerId).ifPresent(worker -> {
            worker.setLastHeartbeatAt(LocalDateTime.now());
            worker.setStatus(WorkerStatus.ACTIVE);
            workerRepository.save(worker);
            workerBroadcastService.broadcastWorkerUpdate(worker);

            WorkerHeartbeat hb = WorkerHeartbeat.builder()
                    .worker(worker)
                    .lastHeartbeatAt(LocalDateTime.now())
                    .status("OK")
                    .build();
            heartbeatRepository.save(hb);
        });
    }

    @PreDestroy
    @Transactional
    public void unregisterWorker() {
        log.info("Unregistering worker node: {}", workerId);
        workerRepository.findById(workerId).ifPresent(worker -> {
            worker.setStatus(WorkerStatus.OFFLINE);
            workerRepository.save(worker);
            workerBroadcastService.broadcastWorkerUpdate(worker);
            eventBroadcastService.broadcastEvent("Worker " + workerId + " shutting down", "WORKER");
        });
    }
}
