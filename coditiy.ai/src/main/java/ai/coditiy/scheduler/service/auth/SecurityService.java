package ai.coditiy.scheduler.service.auth;

import ai.coditiy.scheduler.model.Job;
import ai.coditiy.scheduler.model.OrganizationMember;
import ai.coditiy.scheduler.model.Queue;
import ai.coditiy.scheduler.model.ScheduledJob;
import ai.coditiy.scheduler.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Arrays;
import java.util.Optional;

@Service("securityService")
@RequiredArgsConstructor
public class SecurityService {

    private final OrganizationMemberRepository organizationMemberRepository;
    private final ProjectRepository projectRepository;
    private final QueueRepository queueRepository;
    private final JobRepository jobRepository;
    private final ScheduledJobRepository scheduledJobRepository;

    private Optional<UserDetailsImpl> getCurrentUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetailsImpl) {
            return Optional.of((UserDetailsImpl) principal);
        }
        return Optional.empty();
    }

    @Transactional(readOnly = true)
    public boolean hasOrgRole(Long orgId, String... allowedRoles) {
        Optional<UserDetailsImpl> currentUserOpt = getCurrentUser();
        if (currentUserOpt.isEmpty()) {
            return false;
        }
        Long userId = currentUserOpt.get().getId();
        Optional<OrganizationMember> membership = organizationMemberRepository
                .findByOrganizationIdAndUserId(orgId, userId);
        
        if (membership.isEmpty()) {
            return false;
        }

        String userRole = membership.get().getRole().name();
        return Arrays.stream(allowedRoles).anyMatch(role -> role.equalsIgnoreCase(userRole));
    }

    @Transactional(readOnly = true)
    public boolean hasProjectRole(Long projectId, String... allowedRoles) {
        return projectRepository.findById(projectId)
                .map(project -> hasOrgRole(project.getOrganization().getId(), allowedRoles))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean hasQueueRole(Long queueId, String... allowedRoles) {
        return queueRepository.findById(queueId)
                .map(queue -> hasProjectRole(queue.getProject().getId(), allowedRoles))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean hasJobRole(Long jobId, String... allowedRoles) {
        return jobRepository.findById(jobId)
                .map(job -> hasQueueRole(job.getQueue().getId(), allowedRoles))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public boolean hasScheduledJobRole(Long schedJobId, String... allowedRoles) {
        return scheduledJobRepository.findById(schedJobId)
                .map(sched -> hasProjectRole(sched.getProject().getId(), allowedRoles))
                .orElse(false);
    }
}
