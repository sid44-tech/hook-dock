package dev.hookdock.delivery;

import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.hookdock.endpoints.WebhookEndpoint;
import dev.hookdock.events.CapturedWebhook;

/**
 * Queues delivery attempts. Attempts are written in the same transaction as
 * whatever triggered them (capture, replay), and a worker picks them up
 * afterwards, so a capture is never lost because a target is down.
 */
@Service
public class DeliveryService {

    private final DeliveryAttemptRepository attempts;
    private final Clock clock;

    public DeliveryService(DeliveryAttemptRepository attempts, Clock clock) {
        this.attempts = attempts;
        this.clock = clock;
    }

    @Transactional
    public Optional<DeliveryAttempt> enqueueAutoForward(CapturedWebhook event, WebhookEndpoint endpoint) {
        if (!endpoint.isForwardingEnabled() || endpoint.getForwardDestinationId() == null) {
            return Optional.empty();
        }
        return Optional.of(enqueue(event.getId(), endpoint.getForwardDestinationId(), TriggerType.AUTO_FORWARD, null));
    }

    /**
     * Callers must have already checked the user can access the event and that
     * the destination belongs to the same project.
     */
    @Transactional
    public DeliveryAttempt enqueueReplay(UUID eventId, UUID destinationId, UUID requestedBy) {
        return enqueue(eventId, destinationId, TriggerType.MANUAL_REPLAY, requestedBy);
    }

    @Transactional(readOnly = true)
    public List<DeliveryAttempt> attemptsFor(UUID eventId) {
        return attempts.findByCapturedWebhookIdOrderByAttemptNumberAsc(eventId);
    }

    DeliveryAttempt enqueue(UUID eventId, UUID destinationId, TriggerType trigger, UUID requestedBy) {
        int next = attempts.maxAttemptNumber(eventId) + 1;
        return attempts.save(new DeliveryAttempt(eventId, destinationId, next, trigger, requestedBy, clock.instant()));
    }
}
