package ai.coditiy.scheduler.controller;

import ai.coditiy.scheduler.dto.JobRequest;
import ai.coditiy.scheduler.dto.JobResponse;
import ai.coditiy.scheduler.model.Job;
import ai.coditiy.scheduler.model.JobLog;
import ai.coditiy.scheduler.model.JobStatus;
import ai.coditiy.scheduler.model.JobType;
import ai.coditiy.scheduler.model.OrganizationMember;
import ai.coditiy.scheduler.repository.OrganizationMemberRepository;
import ai.coditiy.scheduler.service.JobService;
import ai.coditiy.scheduler.service.auth.UserDetailsImpl;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class JobController {

    private final JobService jobService;
    private final OrganizationMemberRepository organizationMemberRepository;

    @PostMapping
    @PreAuthorize("@securityService.hasQueueRole(#request.queueId, 'ADMIN', 'MEMBER')")
    public ResponseEntity<JobResponse> createJob(@Valid @RequestBody JobRequest request) {
        return ResponseEntity.ok(jobService.createJob(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("@securityService.hasJobRole(#id, 'ADMIN', 'MEMBER', 'VIEWER')")
    public ResponseEntity<JobResponse> getJobById(@PathVariable Long id) {
        return ResponseEntity.ok(jobService.getJobById(id));
    }

    @GetMapping("/{id}/logs")
    @PreAuthorize("@securityService.hasJobRole(#id, 'ADMIN', 'MEMBER', 'VIEWER')")
    public ResponseEntity<List<JobLog>> getJobLogs(@PathVariable Long id) {
        return ResponseEntity.ok(jobService.getJobLogs(id));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("@securityService.hasJobRole(#id, 'ADMIN', 'MEMBER')")
    public ResponseEntity<JobResponse> cancelJob(@PathVariable Long id) {
        return ResponseEntity.ok(jobService.cancelJob(id));
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("@securityService.hasJobRole(#id, 'ADMIN')")
    public ResponseEntity<JobResponse> retryJob(@PathVariable Long id) {
        return ResponseEntity.ok(jobService.retryJob(id));
    }

    @GetMapping
    public ResponseEntity<Page<JobResponse>> getJobs(
            @RequestParam(required = false) Long queueId,
            @RequestParam(required = false) JobStatus status,
            @RequestParam(required = false) JobType type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        List<OrganizationMember> memberships = organizationMemberRepository.findByUserId(userDetails.getId());
        List<Long> orgIds = memberships.stream()
                .map(m -> m.getOrganization().getId())
                .collect(Collectors.toList());

        Specification<Job> spec = Specification.where((root, query, cb) -> 
            root.get("queue").get("project").get("organization").get("id").in(orgIds)
        );

        if (queueId != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("queue").get("id"), queueId));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }
        if (type != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("type"), type));
        }
        if (startDate != null) {
            spec = spec.and((root, query, cb) -> cb.greaterThanOrEqualTo(root.get("createdAt"), startDate));
        }
        if (endDate != null) {
            spec = spec.and((root, query, cb) -> cb.lessThanOrEqualTo(root.get("createdAt"), endDate));
        }

        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(jobService.listJobs(spec, pageRequest));
    }
}
