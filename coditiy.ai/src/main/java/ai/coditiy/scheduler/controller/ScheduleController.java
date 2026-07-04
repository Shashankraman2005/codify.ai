package ai.coditiy.scheduler.controller;

import ai.coditiy.scheduler.dto.ScheduleRequest;
import ai.coditiy.scheduler.dto.ScheduleResponse;
import ai.coditiy.scheduler.service.schedule.ScheduleService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@Tag(name = "Schedules", description = "Quartz Schedule Management APIs")
public class ScheduleController {

    private final ScheduleService scheduleService;

    @PostMapping("/projects/{projectId}/schedules")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create a new Quartz schedule for a project queue")
    public ResponseEntity<ScheduleResponse> createSchedule(
            @PathVariable Long projectId,
            @Valid @RequestBody ScheduleRequest request) {
        return new ResponseEntity<>(scheduleService.createSchedule(projectId, request), HttpStatus.CREATED);
    }

    @GetMapping("/projects/{projectId}/schedules")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get all schedules for a project")
    public ResponseEntity<List<ScheduleResponse>> getProjectSchedules(@PathVariable Long projectId) {
        return ResponseEntity.ok(scheduleService.getSchedulesForProject(projectId));
    }

    @GetMapping("/schedules/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get schedule details by ID")
    public ResponseEntity<ScheduleResponse> getSchedule(@PathVariable Long id) {
        return ResponseEntity.ok(scheduleService.getSchedule(id));
    }

    @DeleteMapping("/schedules/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Delete a schedule")
    public ResponseEntity<Void> deleteSchedule(@PathVariable Long id) {
        scheduleService.deleteSchedule(id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/schedules/{id}/pause")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Pause a schedule")
    public ResponseEntity<ScheduleResponse> pauseSchedule(@PathVariable Long id) {
        return ResponseEntity.ok(scheduleService.pauseSchedule(id));
    }

    @PutMapping("/schedules/{id}/resume")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Resume a schedule")
    public ResponseEntity<ScheduleResponse> resumeSchedule(@PathVariable Long id) {
        return ResponseEntity.ok(scheduleService.resumeSchedule(id));
    }
}
