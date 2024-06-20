package dev.hookdock.delivery;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Polls the delivery_attempt table for pending work. Multiple instances can run
 * at once; the conditional update in {@link DeliveryAttemptRepository#claim}
 * makes sure each attempt is only sent once.
 */
@Component
@ConditionalOnProperty(prefix = "hookdock.delivery", name = "worker-enabled", havingValue = "true",
        matchIfMissing = true)
class DeliveryWorker {

    private static final Logger log = LoggerFactory.getLogger(DeliveryWorker.class);

    private final DeliveryAttemptRepository attempts;
    private final DeliveryExecutor executor;
    private final DeliveryProperties properties;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final String workerId = "worker-" + UUID.randomUUID().toString().substring(0, 8);

    DeliveryWorker(DeliveryAttemptRepository attempts, DeliveryExecutor executor, DeliveryProperties properties,
            TransactionTemplate tx, Clock clock) {
        this.attempts = attempts;
        this.executor = executor;
        this.properties = properties;
        this.tx = tx;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${hookdock.delivery.poll-interval:PT2S}")
    void poll() {
        List<UUID> pending = attempts.findPendingIds(PageRequest.of(0, properties.batchSize()));
        for (UUID id : pending) {
            if (claim(id)) {
                executor.execute(id);
            }
        }
    }

    private boolean claim(UUID id) {
        Instant now = clock.instant();
        Integer claimed = tx.execute(status -> attempts.claim(id, workerId, now, now.plus(properties.leaseDuration())));
        if (claimed == null || claimed == 0) {
            log.debug("Attempt {} was claimed by another worker", id);
            return false;
        }
        return true;
    }
}
