package ai.coditiy.scheduler.repository;

import ai.coditiy.scheduler.model.RetryPolicy;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RetryPolicyRepository extends JpaRepository<RetryPolicy, Long> {
}
