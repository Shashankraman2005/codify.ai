package ai.coditiy.scheduler.service.job;

import ai.coditiy.scheduler.repository.JobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class ScheduledJobPromoter {

    private final JobRepository jobRepository;

    @Scheduled(fixedDelay = 1000)
    @Transactional
    public void promoteScheduledJobs() {
        LocalDateTime now = LocalDateTime.now();
        int updated = jobRepository.promoteScheduledJobs(now);
        if (updated > 0) {
            log.info("Promoted {} jobs from SCHEDULED to QUEUED status", updated);
        }
    }
}
