package ai.coditiy.scheduler.service.worker;

import ai.coditiy.scheduler.model.RetryPolicy;
import org.springframework.stereotype.Component;

@Component
public class RetryBackoffCalculator {

    public int calculateDelay(RetryPolicy policy, int attempt) {
        if (policy == null) {
            return 10; // Default 10 seconds
        }

        int baseDelay = policy.getBaseDelaySeconds();
        int maxDelay = policy.getMaxDelaySeconds();

        switch (policy.getStrategyType()) {
            case LINEAR:
                // base_delay + (attempt - 1) * base_delay (or a fixed step)
                return Math.min(baseDelay + (attempt - 1) * baseDelay, maxDelay);
            case EXPONENTIAL:
                // base_delay * 2^(attempt - 1)
                double expDelay = baseDelay * Math.pow(2, attempt - 1);
                return Math.min((int) expDelay, maxDelay);
            case FIXED:
            default:
                return baseDelay;
        }
    }
}
