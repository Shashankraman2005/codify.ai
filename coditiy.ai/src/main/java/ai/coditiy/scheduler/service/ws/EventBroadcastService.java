package ai.coditiy.scheduler.service.ws;

import ai.coditiy.scheduler.dto.EventResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class EventBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastEvent(String message, String type) {
        EventResponse event = EventResponse.builder()
                .id(UUID.randomUUID().toString())
                .message(message)
                .type(type)
                .timestamp(LocalDateTime.now())
                .build();
        
        log.debug("Broadcasting event: [{}] {}", type, message);
        messagingTemplate.convertAndSend("/topic/events", event);
    }
}
