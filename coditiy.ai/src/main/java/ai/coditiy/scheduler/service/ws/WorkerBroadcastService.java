package ai.coditiy.scheduler.service.ws;

import ai.coditiy.scheduler.model.Worker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class WorkerBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastWorkerUpdate(Worker worker) {
        log.debug("Broadcasting worker update for Worker ID: {}", worker.getWorkerId());
        messagingTemplate.convertAndSend("/topic/workers", worker);
    }
}
