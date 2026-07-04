package ai.coditiy.scheduler.repository;

import ai.coditiy.scheduler.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface ProjectRepository extends JpaRepository<Project, Long> {
    List<Project> findByOrganizationId(Long organizationId);
}
