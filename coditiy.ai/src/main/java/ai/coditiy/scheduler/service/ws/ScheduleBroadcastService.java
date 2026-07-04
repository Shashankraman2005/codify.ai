package ai.coditiy.scheduler.service.ws;

import ai.coditiy.scheduler.dto.ScheduleResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduleBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastScheduleUpdate(ScheduleResponse schedule) {
        log.debug("Broadcasting schedule update for Schedule ID: {}", schedule.getId());
        messagingTemplate.convertAndSend("/topic/schedules", schedule);
    }
}
