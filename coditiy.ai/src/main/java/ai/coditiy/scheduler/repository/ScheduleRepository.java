package ai.coditiy.scheduler.repository;

import ai.coditiy.scheduler.model.Schedule;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ScheduleRepository extends JpaRepository<Schedule, Long> {
    List<Schedule> findByProjectId(Long projectId);
}
