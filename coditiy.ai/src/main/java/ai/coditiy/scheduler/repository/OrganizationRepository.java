package ai.coditiy.scheduler.repository;

import ai.coditiy.scheduler.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, Long> {
}
