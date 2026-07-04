package ai.coditiy.scheduler.repository;

import ai.coditiy.scheduler.model.DistributedLock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface DistributedLockRepository extends JpaRepository<DistributedLock, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM DistributedLock l WHERE l.lockName = :lockName")
    Optional<DistributedLock> findAndLockByName(@Param("lockName") String lockName);
}
