package ai.coditiy.scheduler.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight health endpoint — no authentication required.
 * Returns the liveness of the database connection and Quartz scheduler.
 */
@RestController
@RequestMapping("/api/health")
public class HealthController {

    private final JdbcTemplate jdbcTemplate;
    private final Scheduler quartzScheduler;

    @Autowired
    public HealthController(JdbcTemplate jdbcTemplate, Scheduler quartzScheduler) {
        this.jdbcTemplate = jdbcTemplate;
        this.quartzScheduler = quartzScheduler;
    }

    @GetMapping
    public Map<String, String> health() {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("status", "UP");
        result.put("database", checkDatabase());
        result.put("scheduler", checkScheduler());
        return result;
    }

    private String checkDatabase() {
        try {
            jdbcTemplate.queryForObject("SELECT 1", Integer.class);
            return "UP";
        } catch (Exception e) {
            return "DOWN";
        }
    }

    private String checkScheduler() {
        try {
            return quartzScheduler.isStarted() && !quartzScheduler.isShutdown()
                    ? "RUNNING"
                    : "STOPPED";
        } catch (SchedulerException e) {
            return "UNKNOWN";
        }
    }
}
