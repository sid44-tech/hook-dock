package dev.hookdock.endpoints;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.hookdock.common.NotFoundException;
import dev.hookdock.ratelimit.RateLimitProperties;

@Service
public class EndpointService {

    private final WebhookEndpointRepository endpoints;
    private final TokenGenerator tokenGenerator;
    private final RateLimitProperties rateLimitProperties;
    private final Clock clock;

    public EndpointService(WebhookEndpointRepository endpoints, TokenGenerator tokenGenerator,
            RateLimitProperties rateLimitProperties, Clock clock) {
        this.endpoints = endpoints;
        this.tokenGenerator = tokenGenerator;
        this.rateLimitProperties = rateLimitProperties;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<WebhookEndpoint> listForProject(UUID projectId) {
        return endpoints.findByProjectIdOrderByCreatedAtAsc(projectId);
    }

    @Transactional(readOnly = true)
    public List<WebhookEndpoint> listForProjects(Collection<UUID> projectIds) {
        return projectIds.isEmpty() ? List.of() : endpoints.findByProjectIdIn(projectIds);
    }

    @Transactional(readOnly = true)
    public WebhookEndpoint get(UUID endpointId) {
        return endpoints.findById(endpointId).orElseThrow(() -> new NotFoundException("Endpoint not found"));
    }

    @Transactional
    public CreatedEndpoint create(UUID projectId, String name) {
        String token = tokenGenerator.newToken();
        WebhookEndpoint endpoint = endpoints.save(
                new WebhookEndpoint(projectId, name.trim(), TokenHasher.hash(token), clock.instant()));
        return new CreatedEndpoint(endpoint, token);
    }

    /**
     * Points the endpoint at a destination for automatic forwarding, or turns
     * forwarding off when {@code destinationId} is null. The caller is
     * responsible for checking the destination belongs to the same project.
     */
    @Transactional
    public void configureForwarding(UUID endpointId, UUID destinationId) {
        get(endpointId).configureForwarding(destinationId);
    }

    @Transactional
    public void setEnabled(UUID endpointId, boolean enabled) {
        get(endpointId).setEnabled(enabled);
    }

    /**
     * Sets this endpoint's rate limit override, or clears it (back to the
     * server default) when {@code requests} is null. A value equal to the
     * server's configured default also clears the override, so the endpoint
     * is shown as using the default and follows it if the default changes
     * later. Takes effect on the endpoint's next request. The caller is
     * responsible for access checks.
     */
    @Transactional
    public void setRateLimit(UUID endpointId, Integer requests) {
        // Compare values, not references: both sides are Integer objects.
        boolean isDefault = requests != null && requests.equals(rateLimitProperties.requests());
        get(endpointId).setRateLimitRequests(isDefault ? null : requests);
    }
}
