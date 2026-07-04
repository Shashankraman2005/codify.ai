package ai.coditiy.scheduler.repository;

import ai.coditiy.scheduler.model.OrganizationMember;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface OrganizationMemberRepository extends JpaRepository<OrganizationMember, Long> {
    List<OrganizationMember> findByUserId(Long userId);
    List<OrganizationMember> findByOrganizationId(Long organizationId);
    Optional<OrganizationMember> findByOrganizationIdAndUserId(Long organizationId, Long userId);
}
