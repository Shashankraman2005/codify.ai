package ai.coditiy.scheduler.integration;

import ai.coditiy.scheduler.dto.ScheduleRequest;
import ai.coditiy.scheduler.dto.ScheduleResponse;
import ai.coditiy.scheduler.model.Organization;
import ai.coditiy.scheduler.model.Project;
import ai.coditiy.scheduler.model.Queue;
import ai.coditiy.scheduler.model.ScheduleType;
import ai.coditiy.scheduler.repository.OrganizationRepository;
import ai.coditiy.scheduler.repository.ProjectRepository;
import ai.coditiy.scheduler.repository.QueueRepository;
import ai.coditiy.scheduler.repository.ScheduleRepository;
import ai.coditiy.scheduler.service.schedule.ScheduleService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@ActiveProfiles("test")
public class Phase4IntegrationIT {

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
        registry.add("spring.quartz.job-store-type", () -> "jdbc");
        registry.add("spring.quartz.jdbc.initialize-schema", () -> "never");
    }

    @Autowired
    private ScheduleService scheduleService;

    @Autowired
    private ScheduleRepository scheduleRepository;

    @Autowired
    private OrganizationRepository organizationRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private QueueRepository queueRepository;

    private Long testProjectId;
    private Long testQueueId;

    @BeforeEach
    void setUp() {
        scheduleRepository.deleteAll();
        queueRepository.deleteAll();
        projectRepository.deleteAll();
        organizationRepository.deleteAll();

        Organization org = new Organization();
        org.setName("Test Org");
        org = organizationRepository.save(org);

        Project project = new Project();
        project.setOrganization(org);
        project.setName("Test Project");
        project = projectRepository.save(project);
        testProjectId = project.getId();

        Queue queue = new Queue();
        queue.setProject(project);
        queue.setName("Test Queue");
        queue.setPriority(1);
        queue.setConcurrencyLimit(5);
        queue = queueRepository.save(queue);
        testQueueId = queue.getId();
    }

    @Test
    void testCronScheduleCreationAndPersistence() {
        ScheduleRequest request = new ScheduleRequest();
        request.setName("Daily Cleanup");
        request.setScheduleType(ScheduleType.CRON);
        request.setCronExpression("0 0 12 * * ?");
        request.setPayload("{\"task\":\"cleanup\"}");
        request.setQueueId(testQueueId);

        ScheduleResponse response = scheduleService.createSchedule(testProjectId, request);

        assertThat(response.getId()).isNotNull();
        assertThat(response.getStatus().name()).isEqualTo("ACTIVE");

        List<ScheduleResponse> schedules = scheduleService.getSchedulesForProject(testProjectId);
        assertThat(schedules).hasSize(1);
        assertThat(schedules.get(0).getName()).isEqualTo("Daily Cleanup");
        assertThat(schedules.get(0).getNextFireTime()).isNotNull();
    }

    @Test
    void testPauseAndResumeSchedule() {
        ScheduleRequest request = new ScheduleRequest();
        request.setName("Fixed Interval Task");
        request.setScheduleType(ScheduleType.FIXED_INTERVAL);
        request.setIntervalSeconds(10);
        request.setPayload("{}");
        request.setQueueId(testQueueId);

        ScheduleResponse schedule = scheduleService.createSchedule(testProjectId, request);
        assertThat(schedule.getStatus().name()).isEqualTo("ACTIVE");

        ScheduleResponse paused = scheduleService.pauseSchedule(schedule.getId());
        assertThat(paused.getStatus().name()).isEqualTo("PAUSED");

        ScheduleResponse resumed = scheduleService.resumeSchedule(schedule.getId());
        assertThat(resumed.getStatus().name()).isEqualTo("ACTIVE");
    }
}
