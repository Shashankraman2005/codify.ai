package ai.coditiy.scheduler.service;

import ai.coditiy.scheduler.model.RetryPolicy;
import ai.coditiy.scheduler.model.RetryStrategy;
import ai.coditiy.scheduler.service.worker.RetryBackoffCalculator;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class RetryBackoffCalculatorTest {

    private final RetryBackoffCalculator calculator = new RetryBackoffCalculator();

    @Test
    public void testFixedDelayStrategy() {
        RetryPolicy policy = RetryPolicy.builder()
                .strategyType(RetryStrategy.FIXED)
                .baseDelaySeconds(5)
                .maxDelaySeconds(60)
                .maxAttempts(5)
                .build();

        assertEquals(5, calculator.calculateDelay(policy, 1));
        assertEquals(5, calculator.calculateDelay(policy, 2));
        assertEquals(5, calculator.calculateDelay(policy, 3));
    }

    @Test
    public void testLinearBackoffStrategy() {
        RetryPolicy policy = RetryPolicy.builder()
                .strategyType(RetryStrategy.LINEAR)
                .baseDelaySeconds(5)
                .maxDelaySeconds(12)
                .maxAttempts(5)
                .build();

        // 5 + (1-1)*5 = 5
        assertEquals(5, calculator.calculateDelay(policy, 1));
        // 5 + (2-1)*5 = 10
        assertEquals(10, calculator.calculateDelay(policy, 2));
        // 5 + (3-1)*5 = 15 -> capped at max 12
        assertEquals(12, calculator.calculateDelay(policy, 3));
    }

    @Test
    public void testExponentialBackoffStrategy() {
        RetryPolicy policy = RetryPolicy.builder()
                .strategyType(RetryStrategy.EXPONENTIAL)
                .baseDelaySeconds(3)
                .maxDelaySeconds(20)
                .maxAttempts(5)
                .build();

        // 3 * 2^0 = 3
        assertEquals(3, calculator.calculateDelay(policy, 1));
        // 3 * 2^1 = 6
        assertEquals(6, calculator.calculateDelay(policy, 2));
        // 3 * 2^2 = 12
        assertEquals(12, calculator.calculateDelay(policy, 3));
        // 3 * 2^3 = 24 -> capped at 20
        assertEquals(20, calculator.calculateDelay(policy, 4));
    }

    @Test
    public void testNullPolicyDefault() {
        assertEquals(10, calculator.calculateDelay(null, 1));
    }
}
