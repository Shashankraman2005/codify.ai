package ai.coditiy.scheduler.repository;

import ai.coditiy.scheduler.model.DeadLetterQueue;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface DeadLetterQueueRepository extends JpaRepository<DeadLetterQueue, Long> {
    Optional<DeadLetterQueue> findByJobId(Long jobId);
    boolean existsByJobId(Long jobId);
}
