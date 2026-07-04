package ai.coditiy.scheduler.controller;

import ai.coditiy.scheduler.dto.QueueRequest;
import ai.coditiy.scheduler.dto.QueueResponse;
import ai.coditiy.scheduler.dto.RetryPolicyRequest;
import ai.coditiy.scheduler.model.*;
import ai.coditiy.scheduler.repository.OrganizationMemberRepository;
import ai.coditiy.scheduler.repository.OrganizationRepository;
import ai.coditiy.scheduler.repository.ProjectRepository;
import ai.coditiy.scheduler.repository.RetryPolicyRepository;
import ai.coditiy.scheduler.service.QueueService;
import ai.coditiy.scheduler.service.ScheduledJobService;
import ai.coditiy.scheduler.dto.ScheduledJobRequest;
import ai.coditiy.scheduler.service.auth.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class WorkspaceController {

    private final OrganizationRepository organizationRepository;
    private final OrganizationMemberRepository organizationMemberRepository;
    private final ProjectRepository projectRepository;
    private final RetryPolicyRepository retryPolicyRepository;
    private final QueueService queueService;
    private final ScheduledJobService scheduledJobService;

    // --- Organizations ---

    @GetMapping("/organizations")
    public ResponseEntity<List<Organization>> getOrganizations() {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<OrganizationMember> memberships = organizationMemberRepository.findByUserId(userDetails.getId());
        List<Organization> orgs = memberships.stream()
                .map(OrganizationMember::getOrganization)
                .collect(Collectors.toList());
        return ResponseEntity.ok(orgs);
    }

    @PostMapping("/organizations")
    @Transactional
    public ResponseEntity<Organization> createOrganization(@RequestBody Map<String, String> request) {
        String name = request.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        Organization org = Organization.builder().name(name).build();
        organizationRepository.save(org);

        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        User user = User.builder().id(userDetails.getId()).build();
        
        OrganizationMember member = OrganizationMember.builder()
                .organization(org)
                .user(user)
                .role(Role.ADMIN)
                .build();
        organizationMemberRepository.save(member);

        return ResponseEntity.ok(org);
    }

    // --- Projects ---

    @GetMapping("/organizations/{orgId}/projects")
    @PreAuthorize("@securityService.hasOrgRole(#orgId, 'ADMIN', 'MEMBER', 'VIEWER')")
    public ResponseEntity<List<Project>> getProjects(@PathVariable Long orgId) {
        List<Project> projects = projectRepository.findByOrganizationId(orgId);
        return ResponseEntity.ok(projects);
    }

    @PostMapping("/organizations/{orgId}/projects")
    @PreAuthorize("@securityService.hasOrgRole(#orgId, 'ADMIN', 'MEMBER')")
    public ResponseEntity<Project> createProject(@PathVariable Long orgId, @RequestBody Map<String, String> request) {
        String name = request.get("name");
        if (name == null || name.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        Organization org = organizationRepository.findById(orgId)
                .orElseThrow(() -> new IllegalArgumentException("Organization not found: " + orgId));

        Project project = Project.builder()
                .name(name)
                .organization(org)
                .build();

        return ResponseEntity.ok(projectRepository.save(project));
    }

    // --- Queues ---

    @GetMapping("/projects/{projectId}/queues")
    @PreAuthorize("@securityService.hasProjectRole(#projectId, 'ADMIN', 'MEMBER', 'VIEWER')")
    public ResponseEntity<List<QueueResponse>> getQueues(@PathVariable Long projectId) {
        return ResponseEntity.ok(queueService.getQueuesForProject(projectId));
    }

    @PostMapping("/projects/{projectId}/queues")
    @PreAuthorize("@securityService.hasProjectRole(#projectId, 'ADMIN')")
    public ResponseEntity<QueueResponse> createQueue(@PathVariable Long projectId, @Valid @RequestBody QueueRequest queueRequest) {
        queueRequest.setProjectId(projectId);
        return ResponseEntity.ok(queueService.createQueue(queueRequest));
    }

    @PutMapping("/queues/{id}")
    @PreAuthorize("@securityService.hasQueueRole(#id, 'ADMIN')")
    public ResponseEntity<QueueResponse> updateQueue(@PathVariable Long id, @Valid @RequestBody QueueRequest queueRequest) {
        return ResponseEntity.ok(queueService.updateQueue(id, queueRequest));
    }

    @PostMapping("/queues/{id}/pause")
    @PreAuthorize("@securityService.hasQueueRole(#id, 'ADMIN')")
    public ResponseEntity<QueueResponse> pauseQueue(@PathVariable Long id) {
        return ResponseEntity.ok(queueService.setPauseState(id, true));
    }

    @PostMapping("/queues/{id}/resume")
    @PreAuthorize("@securityService.hasQueueRole(#id, 'ADMIN')")
    public ResponseEntity<QueueResponse> resumeQueue(@PathVariable Long id) {
        return ResponseEntity.ok(queueService.setPauseState(id, false));
    }

    // --- Retry Policies ---

    @GetMapping("/retry-policies")
    public ResponseEntity<List<RetryPolicy>> getRetryPolicies() {
        return ResponseEntity.ok(retryPolicyRepository.findAll());
    }

    @PostMapping("/retry-policies")
    public ResponseEntity<RetryPolicy> createRetryPolicy(@Valid @RequestBody RetryPolicyRequest request) {
        RetryPolicy policy = RetryPolicy.builder()
                .name(request.getName())
                .strategyType(request.getStrategyType())
                .baseDelaySeconds(request.getBaseDelaySeconds())
                .maxDelaySeconds(request.getMaxDelaySeconds())
                .maxAttempts(request.getMaxAttempts())
                .build();
        return ResponseEntity.ok(retryPolicyRepository.save(policy));
    }

    // --- Scheduled (Cron) Jobs ---

    @GetMapping("/projects/{projectId}/scheduled-jobs")
    @PreAuthorize("@securityService.hasProjectRole(#projectId, 'ADMIN', 'MEMBER', 'VIEWER')")
    public ResponseEntity<List<ScheduledJob>> getScheduledJobs(@PathVariable Long projectId) {
        return ResponseEntity.ok(scheduledJobService.getScheduledJobsForProject(projectId));
    }

    @PostMapping("/projects/{projectId}/scheduled-jobs")
    @PreAuthorize("@securityService.hasProjectRole(#projectId, 'ADMIN', 'MEMBER')")
    public ResponseEntity<ScheduledJob> createScheduledJob(@PathVariable Long projectId, @Valid @RequestBody ScheduledJobRequest request) {
        request.setProjectId(projectId);
        return ResponseEntity.ok(scheduledJobService.createScheduledJob(request));
    }

    @PutMapping("/scheduled-jobs/{id}")
    @PreAuthorize("@securityService.hasScheduledJobRole(#id, 'ADMIN', 'MEMBER')")
    public ResponseEntity<ScheduledJob> updateScheduledJob(@PathVariable Long id, @Valid @RequestBody ScheduledJobRequest request) {
        return ResponseEntity.ok(scheduledJobService.updateScheduledJob(id, request));
    }

    @DeleteMapping("/scheduled-jobs/{id}")
    @PreAuthorize("@securityService.hasScheduledJobRole(#id, 'ADMIN')")
    public ResponseEntity<Void> deleteScheduledJob(@PathVariable Long id) {
        scheduledJobService.deleteScheduledJob(id);
        return ResponseEntity.noContent().build();
    }
}
