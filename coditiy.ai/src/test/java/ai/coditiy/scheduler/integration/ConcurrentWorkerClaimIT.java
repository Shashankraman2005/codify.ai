package ai.coditiy.scheduler.integration;

import ai.coditiy.scheduler.model.*;
import ai.coditiy.scheduler.repository.*;
import ai.coditiy.scheduler.service.worker.JobClaimService;
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
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
public class ConcurrentWorkerClaimIT {

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

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private OrganizationMemberRepository organizationMemberRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private QueueRepository queueRepository;

    @Autowired
    private JobRepository jobRepository;

    @Autowired
    private JobClaimService jobClaimService;

    @Test
    public void testConcurrentWorkerClaimsNoDuplicates() throws InterruptedException {
        // 1. Setup metadata hierarchy
        User user = User.builder()
                .username("testuser")
                .email("testuser@example.com")
                .passwordHash("hash")
                .build();
        userRepository.save(user);

        Organization org = Organization.builder()
                .name("Test Org")
                .build();
        organizationRepository.save(org);

        OrganizationMember member = OrganizationMember.builder()
                .organization(org)
                .user(user)
                .role(Role.ADMIN)
                .build();
        organizationMemberRepository.save(member);

        Project project = Project.builder()
                .name("Test Project")
                .organization(org)
                .build();
        projectRepository.save(project);

        ai.coditiy.scheduler.model.Queue queue = ai.coditiy.scheduler.model.Queue.builder()
                .name("concurrency-test-queue")
                .project(project)
                .priority(1)
                .concurrencyLimit(10)
                .isPaused(false)
                .build();
        queueRepository.save(queue);

        // 2. Insert 100 QUEUED jobs
        int jobCount = 100;
        List<Job> jobs = new ArrayList<>();
        for (int i = 0; i < jobCount; i++) {
            Job job = Job.builder()
                    .queue(queue)
                    .type(JobType.IMMEDIATE)
                    .status(JobStatus.QUEUED)
                    .payload("{\"jobNum\":" + i + "}")
                    .priority(1)
                    .attemptCount(0)
                    .maxAttempts(3)
                    .scheduledAt(LocalDateTime.now())
                    .build();
            jobs.add(job);
        }
        jobRepository.saveAll(jobs);

        // 3. Setup concurrent threads claiming from the same queue
        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        
        // Concurrent map to collect job IDs claimed by each worker ID
        Map<Long, String> claimedJobsMap = new ConcurrentHashMap<>();
        List<Callable<Void>> tasks = new ArrayList<>();

        for (int t = 0; t < threadCount; t++) {
            final String workerId = "worker-thread-" + t;
            tasks.add(() -> {
                // Wait for the latch to open so all threads start exactly at the same millisecond
                latch.await();
                
                while (true) {
                    Optional<Job> claimedJobOpt = jobClaimService.claimJob(queue.getId(), workerId);
                    if (claimedJobOpt.isPresent()) {
                        Job claimedJob = claimedJobOpt.get();
                        // Put in map. If key already exists, we have a duplicate claim!
                        String previousClaimer = claimedJobsMap.put(claimedJob.getId(), workerId);
                        if (previousClaimer != null) {
                            throw new IllegalStateException("DUPLICATE CLAIM: Job " + claimedJob.getId() + 
                                    " was claimed by both " + previousClaimer + " and " + workerId);
                        }
                    } else {
                        // Queue empty or no more jobs available
                        break;
                    }
                }
                return null;
            });
        }

        // Submit all tasks
        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
            futures.add(executor.submit(task));
        }

        // Open the gate!
        latch.countDown();

        // Wait for execution completion
        executor.shutdown();
        boolean terminated = executor.awaitTermination(20, TimeUnit.SECONDS);
        assertTrue(terminated, "Executor did not terminate in time");

        // Verify no exceptions were thrown (which would happen on duplicate claim)
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (ExecutionException e) {
                // Assert fail if any exception
                throw new AssertionError("Test thread failed with exception", e.getCause());
            }
        }

        // 4. Assertions
        assertEquals(jobCount, claimedJobsMap.size(), "Total claimed jobs count should match inserted count");
        
        List<Job> updatedJobs = jobRepository.findAll();
        for (Job job : updatedJobs) {
            assertEquals(JobStatus.CLAIMED, job.getStatus(), "All jobs must end up in CLAIMED state");
            assertTrue(job.getWorkerId() != null && job.getWorkerId().startsWith("worker-thread-"), 
                    "Each job must have a valid worker thread ID");
            assertEquals(1, job.getAttemptCount(), "Attempt count should be exactly 1");
        }
    }
}
