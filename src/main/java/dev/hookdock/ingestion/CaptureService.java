package dev.hookdock.ingestion;

import java.time.Clock;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.hookdock.delivery.DeliveryService;
import dev.hookdock.endpoints.TokenHasher;
import dev.hookdock.endpoints.WebhookEndpoint;
import dev.hookdock.endpoints.WebhookEndpointRepository;
import dev.hookdock.events.CapturedWebhook;
import dev.hookdock.events.CapturedWebhookRepository;
import dev.hookdock.ratelimit.RateLimitDecision;
import dev.hookdock.ratelimit.RateLimitStatus;
import dev.hookdock.ratelimit.RateLimiter;

@Service
public class CaptureService {

    private final WebhookEndpointRepository endpoints;
    private final CapturedWebhookRepository events;
    private final HeaderRedactor headerRedactor;
    private final DeliveryService deliveryService;
    private final RateLimiter rateLimiter;
    private final RecentRejections recentRejections;
    private final Clock clock;

    public CaptureService(WebhookEndpointRepository endpoints, CapturedWebhookRepository events,
            HeaderRedactor headerRedactor, DeliveryService deliveryService, RateLimiter rateLimiter,
            RecentRejections recentRejections, Clock clock) {
        this.endpoints = endpoints;
        this.events = events;
        this.headerRedactor = headerRedactor;
        this.deliveryService = deliveryService;
        this.rateLimiter = rateLimiter;
        this.recentRejections = recentRejections;
        this.clock = clock;
    }

    /**
     * Persists the inbound request, and queues a forward if the endpoint has one
     * configured. Nothing is stored when the token doesn't match an enabled
     * endpoint or the endpoint is over its rate limit.
     * <p>
     * The limit is checked after the endpoint lookup so that unknown tokens
     * never create limiter state and a disabled endpoint still answers 404.
     */
    @Transactional
    public CaptureResult capture(String token, InboundRequest request) {
        Optional<WebhookEndpoint> endpoint = endpoints.findByTokenHash(TokenHasher.hash(token))
                .filter(WebhookEndpoint::isEnabled);
        if (endpoint.isEmpty()) {
            return new CaptureResult.NotFound();
        }

        RateLimitDecision decision = rateLimiter.tryAcquire(
                endpoint.get().getId(), endpoint.get().getRateLimitRequests());
        if (!decision.allowed()) {
            RateLimitStatus status = decision.status().orElseThrow();
            recentRejections.record(new Rejection(endpoint.get().getId(), clock.instant(), request.method(),
                    RejectionReason.RATE_LIMITED, status.limit()));
            return new CaptureResult.RateLimited(status);
        }

        ProviderDetector.ProviderInfo provider = ProviderDetector.detect(request.headers());
        CapturedWebhook event = new CapturedWebhook(
                endpoint.get().getId(),
                clock.instant(),
                request.method(),
                request.path(),
                request.queryString(),
                headerRedactor.redact(request.headers()),
                request.body(),
                request.contentType(),
                provider.provider(),
                provider.eventId());
        CapturedWebhook saved = events.save(event);
        deliveryService.enqueueAutoForward(saved, endpoint.get());
        return new CaptureResult.Captured(saved, decision.status());
    }
}
