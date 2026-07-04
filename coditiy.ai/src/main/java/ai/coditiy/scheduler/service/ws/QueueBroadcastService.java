package ai.coditiy.scheduler.service.ws;

import ai.coditiy.scheduler.dto.QueueResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class QueueBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastQueueUpdate(QueueResponse queue) {
        log.debug("Broadcasting queue update for Queue ID: {}", queue.getId());
        messagingTemplate.convertAndSend("/topic/queues", queue);
    }
}
