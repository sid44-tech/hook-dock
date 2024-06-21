package dev.hookdock.delivery;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.time.Clock;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import dev.hookdock.destinations.Destination;
import dev.hookdock.destinations.DestinationPolicy;
import dev.hookdock.destinations.DestinationRejectedException;
import dev.hookdock.destinations.DestinationRepository;
import dev.hookdock.events.CapturedWebhook;
import dev.hookdock.events.CapturedWebhookRepository;

/**
 * Runs one claimed attempt end to end. The HTTP call happens outside any
 * database transaction so a slow target doesn't hold a connection open.
 */
@Component
class DeliveryExecutor {

    private static final Logger log = LoggerFactory.getLogger(DeliveryExecutor.class);

    private final DeliveryAttemptRepository attempts;
    private final CapturedWebhookRepository events;
    private final DestinationRepository destinations;
    private final DestinationPolicy policy;
    private final WebhookSender sender;
    private final TransactionTemplate tx;
    private final Clock clock;

    DeliveryExecutor(DeliveryAttemptRepository attempts, CapturedWebhookRepository events,
            DestinationRepository destinations, DestinationPolicy policy, WebhookSender sender,
            TransactionTemplate tx, Clock clock) {
        this.attempts = attempts;
        this.events = events;
        this.destinations = destinations;
        this.policy = policy;
        this.sender = sender;
        this.tx = tx;
        this.clock = clock;
    }

    void execute(UUID attemptId) {
        DeliveryAttempt attempt = attempts.findById(attemptId).orElseThrow();
        CapturedWebhook event = events.findById(attempt.getCapturedWebhookId()).orElseThrow();
        Destination destination = destinations.findById(attempt.getDestinationId()).orElseThrow();

        if (!destination.isEnabled()) {
            complete(attemptId, a -> a.block(ErrorCategory.DESTINATION_DISABLED,
                    "Destination is disabled", now()));
            return;
        }

        URI uri;
        try {
            // Re-check right before sending: DNS may have changed since the
            // destination was saved.
            uri = policy.validate(destination.getUrl());
        } catch (DestinationRejectedException e) {
            complete(attemptId, a -> a.block(ErrorCategory.POLICY_BLOCKED, e.getMessage(), now()));
            return;
        }

        OutboundRequest request = new OutboundRequest(event.getMethod(), withQuery(uri, event.getQueryString()),
                outboundHeaders(event, attempt), event.getRequestBody() == null ? new byte[0] : event.getRequestBody());
        try {
            OutboundResponse response = sender.send(request);
            if (response.isSuccess()) {
                complete(attemptId, a -> a.succeed(response.status(), response.headers(), response.bodyExcerpt(), now()));
            } else {
                complete(attemptId, a -> a.failWithResponse(response.status(), response.headers(),
                        response.bodyExcerpt(), now()));
            }
        } catch (HttpConnectTimeoutException e) {
            complete(attemptId, a -> a.fail(ErrorCategory.TIMEOUT, "Connect timed out", now()));
        } catch (HttpTimeoutException e) {
            complete(attemptId, a -> a.fail(ErrorCategory.TIMEOUT, "Request timed out", now()));
        } catch (ConnectException e) {
            complete(attemptId, a -> a.fail(ErrorCategory.CONNECTION, "Connection refused", now()));
        } catch (IOException e) {
            complete(attemptId, a -> a.fail(ErrorCategory.CONNECTION, e.getClass().getSimpleName()
                    + (e.getMessage() == null ? "" : ": " + e.getMessage()), now()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            complete(attemptId, a -> a.fail(ErrorCategory.INTERNAL, "Interrupted", now()));
        } catch (RuntimeException e) {
            log.warn("Delivery attempt {} failed unexpectedly", attemptId, e);
            complete(attemptId, a -> a.fail(ErrorCategory.INTERNAL, e.getClass().getSimpleName(), now()));
        }
    }

    private Map<String, List<String>> outboundHeaders(CapturedWebhook event, DeliveryAttempt attempt) {
        Map<String, List<String>> headers = new LinkedHashMap<>(OutboundHeaderFilter.filter(event.getRequestHeaders()));
        headers.put("x-hookdock-event-id", List.of(event.getId().toString()));
        headers.put("x-hookdock-attempt-id", List.of(attempt.getId().toString()));
        return headers;
    }

    private static URI withQuery(URI base, String query) {
        if (query == null || query.isEmpty()) {
            return base;
        }
        String separator = base.getRawQuery() == null ? "?" : "&";
        return URI.create(base.toString() + separator + query);
    }

    private void complete(UUID attemptId, Consumer<DeliveryAttempt> update) {
        tx.executeWithoutResult(status -> update.accept(attempts.findById(attemptId).orElseThrow()));
    }

    private Instant now() {
        return clock.instant();
    }
}
