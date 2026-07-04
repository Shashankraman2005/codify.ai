package ai.coditiy.scheduler.integration;

import ai.coditiy.scheduler.model.*;
import ai.coditiy.scheduler.repository.*;
import ai.coditiy.scheduler.service.worker.QueuePollerManager;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
@ActiveProfiles({"test", "worker"})
public class QueueConcurrencyLimitIT {

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
        registry.add("scheduler.worker.poll-interval-ms", () -> "100");
    }

    @Autowired private UserRepository userRepository;
    @Autowired private OrganizationRepository organizationRepository;
    @Autowired private ProjectRepository projectRepository;
    @Autowired private QueueRepository queueRepository;
    @Autowired private JobRepository jobRepository;
    @Autowired private QueuePollerManager queuePollerManager;

    @Test
    public void testConcurrencyLimitRespected() throws InterruptedException {
        User user = User.builder().username("cluser").email("cl@test.com").passwordHash("hash").build();
        userRepository.save(user);

        Organization org = Organization.builder().name("Org CL").build();
        organizationRepository.save(org);

        Project project = Project.builder().name("Proj CL").organization(org).build();
        projectRepository.save(project);

        // Queue with concurrency limit = 2
        Queue queue = Queue.builder()
                .name("Concurrency Queue")
                .project(project)
                .priority(1)
                .concurrencyLimit(2)
                .isPaused(false)
                .build();
        queue = queueRepository.save(queue);

        // Create 10 jobs that sleep for 2 seconds each
        List<Job> jobs = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Job job = Job.builder()
                    .queue(queue)
                    .type(JobType.IMMEDIATE)
                    .status(JobStatus.QUEUED)
                    .payload("{\"sleep\":2000}")
                    .priority(1)
                    .attemptCount(0)
                    .maxAttempts(3)
                    .scheduledAt(LocalDateTime.now())
                    .build();
            jobs.add(job);
        }
        jobRepository.saveAll(jobs);

        // Start poller loop
        queuePollerManager.syncPollers();

        // Wait a short time to allow exactly 2 jobs to be picked up
        Thread.sleep(1000);

        long runningJobs = jobRepository.countByQueueIdAndStatus(queue.getId(), JobStatus.RUNNING);
        long claimedJobs = jobRepository.countByQueueIdAndStatus(queue.getId(), JobStatus.CLAIMED);
        
        // Since limit is 2, active threads <= 2
        long active = runningJobs + claimedJobs;
        
        assertTrue(active > 0, "Poller should have picked up some jobs");
        assertTrue(active <= 2, "Poller exceeded concurrency limit. Active jobs: " + active);

        // Cleanup
        queuePollerManager.stopAll();
    }
}
