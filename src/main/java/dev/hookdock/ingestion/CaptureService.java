package dev.hookdock.ingestion;

import java.time.Clock;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.hookdock.endpoints.TokenHasher;
import dev.hookdock.endpoints.WebhookEndpoint;
import dev.hookdock.endpoints.WebhookEndpointRepository;
import dev.hookdock.events.CapturedWebhook;
import dev.hookdock.events.CapturedWebhookRepository;

@Service
public class CaptureService {

    private final WebhookEndpointRepository endpoints;
    private final CapturedWebhookRepository events;
    private final HeaderRedactor headerRedactor;
    private final Clock clock;

    public CaptureService(WebhookEndpointRepository endpoints, CapturedWebhookRepository events,
            HeaderRedactor headerRedactor, Clock clock) {
        this.endpoints = endpoints;
        this.events = events;
        this.headerRedactor = headerRedactor;
        this.clock = clock;
    }

    /**
     * Persists the inbound request. Returns empty if the token doesn't match an
     * enabled endpoint, in which case nothing is stored.
     */
    @Transactional
    public Optional<CapturedWebhook> capture(String token, InboundRequest request) {
        Optional<WebhookEndpoint> endpoint = endpoints.findByTokenHash(TokenHasher.hash(token))
                .filter(WebhookEndpoint::isEnabled);
        if (endpoint.isEmpty()) {
            return Optional.empty();
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
        return Optional.of(events.save(event));
    }
}
