package ai.coditiy.scheduler.service.worker;

import ai.coditiy.scheduler.model.Job;
import ai.coditiy.scheduler.model.JobExecution;
import ai.coditiy.scheduler.model.JobStatus;
import ai.coditiy.scheduler.repository.JobExecutionRepository;
import ai.coditiy.scheduler.repository.JobRepository;
import ai.coditiy.scheduler.service.JobService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class JobClaimService {

    private final JobRepository jobRepository;
    private final JobExecutionRepository jobExecutionRepository;
    private final JobService jobService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<Job> claimJob(Long queueId, String workerId) {
        LocalDateTime now = LocalDateTime.now();
        Optional<Job> jobOpt = jobRepository.claimNextJob(queueId, now);
        
        if (jobOpt.isPresent()) {
            Job job = jobOpt.get();
            
            // Atomically update status
            job.setStatus(JobStatus.CLAIMED);
            job.setWorkerId(workerId);
            job.setAttemptCount(job.getAttemptCount() + 1);
            jobRepository.save(job);

            // Record execution log start
            JobExecution execution = JobExecution.builder()
                    .job(job)
                    .workerId(workerId)
                    .attemptNumber(job.getAttemptCount())
                    .startedAt(LocalDateTime.now())
                    .status("CLAIMED")
                    .build();
            jobExecutionRepository.save(execution);

            jobService.logEvent(job, "INFO", "Job claimed by worker " + workerId + " (Attempt " + job.getAttemptCount() + "/" + job.getMaxAttempts() + ")");
            return Optional.of(job);
        }
        return Optional.empty();
    }
}
