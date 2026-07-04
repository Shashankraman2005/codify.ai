package ai.coditiy.scheduler.integration;

import ai.coditiy.scheduler.model.*;
import ai.coditiy.scheduler.repository.*;
import ai.coditiy.scheduler.service.worker.JobExecutorService;
import ai.coditiy.scheduler.service.worker.RetryBackoffCalculator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public class RetryPolicyAndDLQIT {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15-alpine")
            .withDatabaseName("scheduler_test")
            .withUsername("test")
            .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired private UserRepository userRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private QueueRepository queueRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private RetryPolicyRepository retryPolicyRepository;
    @Autowired private JobExecutionRepository jobExecutionRepository;
    @Autowired private DeadLetterQueueRepository dlqRepository;
    @Autowired private JobExecutorService executorService;
    @Autowired private RetryBackoffCalculator retryBackoffCalculator;

    private Queue setupQueueWithRetry(RetryStrategy type, int maxAttempts) {
        User user = User.builder().username("retryuser" + type).email("u" + type + "@test.com").passwordHash("hash").build();
        userRepository.save(user);

        Organization org = Organization.builder().name("Org " + type).build();
        organizationRepository.save(org);

        Project project = Project.builder().name("Proj " + type).organization(org).build();
        projectRepository.save(project);

        RetryPolicy policy = RetryPolicy.builder()
                .name(type + " Policy")
                .strategyType(type)
                .maxAttempts(maxAttempts)
                .baseDelaySeconds(5)
                .maxDelaySeconds(60)
                .build();
        retryPolicyRepository.save(policy);

        Queue queue = Queue.builder()
                .name("Queue " + type)
                .project(project)
                .priority(1)
                .concurrencyLimit(2)
                .retryPolicy(policy)
                .isPaused(false)
                .build();
        return queueRepository.save(queue);
    }

    @Test
    public void testFixedRetryAndDLQ() {
        Queue queue = setupQueueWithRetry(RetryStrategy.FIXED, 2);
        
        // Ensure delay calc works properly
        assertEquals(5, retryBackoffCalculator.calculateDelay(queue.getRetryPolicy(), 1));

        Job job = Job.builder()
                .queue(queue)
                .type(JobType.IMMEDIATE)
                .status(JobStatus.QUEUED)
                .payload("{\"fail\":true}")
                .priority(1)
                .attemptCount(0)
                .maxAttempts(queue.getRetryPolicy().getMaxAttempts())
                .scheduledAt(LocalDateTime.now())
                .build();
        job = jobRepository.save(job);

        // First execution attempt - fails because of {"fail":true}
        job.setStatus(JobStatus.CLAIMED);
        job.setAttemptCount(1);
        job = jobRepository.save(job);
        
        JobExecution exec1 = JobExecution.builder()
            .job(job).workerId("worker-1").status("CLAIMED").startedAt(LocalDateTime.now()).attemptNumber(1)
            .build();
        jobExecutionRepository.save(exec1);

        executorService.executeJob(job.getId(), "worker-1");

        // Should be rescheduled (status SCHEDULED, attempt 1 < max 2)
        Job updatedJob = jobRepository.findById(job.getId()).get();
        assertEquals(JobStatus.SCHEDULED, updatedJob.getStatus());
        assertEquals(1, updatedJob.getAttemptCount());
        
        // Second execution attempt - fails again and should DLQ
        updatedJob.setStatus(JobStatus.CLAIMED);
        updatedJob.setAttemptCount(2);
        jobRepository.save(updatedJob);

        JobExecution exec2 = JobExecution.builder()
            .job(updatedJob).workerId("worker-1").status("CLAIMED").startedAt(LocalDateTime.now()).attemptNumber(2)
            .build();
        jobExecutionRepository.save(exec2);

        executorService.executeJob(updatedJob.getId(), "worker-1");

        Job finalJob = jobRepository.findById(job.getId()).get();
        assertEquals(JobStatus.DEAD_LETTER, finalJob.getStatus());
        assertEquals(2, finalJob.getAttemptCount());

        Optional<DeadLetterQueue> dlq = dlqRepository.findByJobId(job.getId());
        assertTrue(dlq.isPresent());
        assertTrue(dlq.get().getFinalError().contains("Simulated job failure"));
    }
}
