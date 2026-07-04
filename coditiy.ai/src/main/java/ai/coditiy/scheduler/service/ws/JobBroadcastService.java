package ai.coditiy.scheduler.service.ws;

import ai.coditiy.scheduler.dto.JobResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobBroadcastService {

    private final SimpMessagingTemplate messagingTemplate;

    public void broadcastJobUpdate(JobResponse job) {
        log.debug("Broadcasting job update for Job ID: {}", job.getId());
        messagingTemplate.convertAndSend("/topic/jobs", job);
    }
}
