package ai.coditiy.scheduler.service.worker;

import ai.coditiy.scheduler.model.DistributedLock;
import ai.coditiy.scheduler.repository.DistributedLockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class LockService {

    private final DistributedLockRepository lockRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean acquireLock(String lockName, String holderId, long leaseTimeSeconds) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = now.plusSeconds(leaseTimeSeconds);

        Optional<DistributedLock> lockOpt = lockRepository.findAndLockByName(lockName);

        if (lockOpt.isEmpty()) {
            try {
                DistributedLock lock = DistributedLock.builder()
                        .lockName(lockName)
                        .holderId(holderId)
                        .expiresAt(expiresAt)
                        .build();
                lockRepository.save(lock);
                return true;
            } catch (Exception e) {
                log.warn("Failed to create lock '{}' (already created by another node): {}", lockName, e.getMessage());
                return false;
            }
        }

        DistributedLock lock = lockOpt.get();
        if (lock.getExpiresAt().isBefore(now) || lock.getHolderId().equals(holderId)) {
            lock.setHolderId(holderId);
            lock.setExpiresAt(expiresAt);
            lockRepository.save(lock);
            return true;
        }

        return false;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void releaseLock(String lockName, String holderId) {
        Optional<DistributedLock> lockOpt = lockRepository.findById(lockName);
        if (lockOpt.isPresent() && lockOpt.get().getHolderId().equals(holderId)) {
            lockRepository.delete(lockOpt.get());
            log.debug("Released lock: {}", lockName);
        }
    }
}
