package ai.coditiy.scheduler.integration;

import ai.coditiy.scheduler.model.*;
import ai.coditiy.scheduler.repository.*;
import ai.coditiy.scheduler.service.worker.ReaperService;
import ai.coditiy.scheduler.service.worker.WorkerHeartbeatService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@Testcontainers
@ActiveProfiles({"test", "worker"})
public class ReaperServiceIT {

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
        // Set short timeout for tests
        registry.add("scheduler.reaper.dead-threshold-ms", () -> "1000");
    }

    @Autowired private UserRepository userRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private QueueRepository queueRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private WorkerRepository workerRepository;
    @Autowired private ReaperService reaperService;
    @Autowired private WorkerHeartbeatService workerHeartbeatService;

    @Test
    public void testReaperRecoversJobsFromDeadWorker() throws InterruptedException {
        User user = User.builder().username("reaperuser").email("reaper@test.com").passwordHash("hash").build();
        userRepository.save(user);

        Organization org = Organization.builder().name("Org Reaper").build();
        organizationRepository.save(org);

        Project project = Project.builder().name("Proj Reaper").organization(org).build();
        projectRepository.save(project);

        Queue queue = Queue.builder()
                .name("Reaper Queue")
                .project(project)
                .priority(1)
                .concurrencyLimit(5)
                .isPaused(false)
                .build();
        queueRepository.save(queue);

        // Simulate a worker sending a heartbeat
        workerHeartbeatService.sendHeartbeat();
        
        // Wait 1.5s to ensure worker becomes "dead" based on 1000ms threshold
        Thread.sleep(1500);

        // Give the "dead" worker a job that is RUNNING
        Worker deadWorker = workerRepository.findAll().get(0);
        String deadWorkerId = deadWorker.getWorkerId();

        Job job = Job.builder()
                .queue(queue)
                .type(JobType.IMMEDIATE)
                .status(JobStatus.RUNNING)
                .workerId(deadWorkerId)
                .payload("{}")
                .priority(1)
                .attemptCount(1)
                .maxAttempts(3)
                .scheduledAt(LocalDateTime.now().minusMinutes(1))
                .build();
        job = jobRepository.save(job);

        // Run the reaper
        reaperService.runReaper();

        // Job should be re-queued and detached from dead worker
        Job recoveredJob = jobRepository.findById(job.getId()).get();
        assertEquals(JobStatus.QUEUED, recoveredJob.getStatus());
        assertEquals(null, recoveredJob.getWorkerId());

        // Worker should be marked DEAD
        Worker updatedWorker = workerRepository.findById(deadWorkerId).get();
        assertEquals(WorkerStatus.DEAD, updatedWorker.getStatus());
    }
}
