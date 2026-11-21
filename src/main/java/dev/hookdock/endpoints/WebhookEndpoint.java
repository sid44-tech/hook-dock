package dev.hookdock.endpoints;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "webhook_endpoint")
public class WebhookEndpoint {

    /**
     * Highest per-endpoint rate limit override. The limiter keeps one
     * timestamp per allowed request, so this also caps its memory per endpoint.
     */
    public static final int MAX_RATE_LIMIT = 10_000;

    @Id
    private UUID id;

    @Column(name = "project_id", nullable = false)
    private UUID projectId;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "forwarding_enabled", nullable = false)
    private boolean forwardingEnabled = false;

    @Column(name = "forward_destination_id")
    private UUID forwardDestinationId;

    /** Requests per rate limit window for this endpoint; null uses the server default. */
    @Column(name = "rate_limit_requests")
    private Integer rateLimitRequests;

    @Column(name = "retention_days", nullable = false)
    private int retentionDays = 30;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WebhookEndpoint() {
    }

    public WebhookEndpoint(UUID projectId, String name, String tokenHash, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.projectId = projectId;
        this.name = name;
        this.tokenHash = tokenHash;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getProjectId() {
        return projectId;
    }

    public String getName() {
        return name;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isForwardingEnabled() {
        return forwardingEnabled;
    }

    public UUID getForwardDestinationId() {
        return forwardDestinationId;
    }

    public void configureForwarding(UUID destinationId) {
        this.forwardDestinationId = destinationId;
        this.forwardingEnabled = destinationId != null;
    }

    public Integer getRateLimitRequests() {
        return rateLimitRequests;
    }

    /**
     * Overrides the server-wide rate limit for this endpoint only, or goes back
     * to the default when {@code requests} is null.
     */
    public void setRateLimitRequests(Integer requests) {
        if (requests != null && (requests < 1 || requests > MAX_RATE_LIMIT)) {
            throw new IllegalArgumentException("Rate limit must be between 1 and " + MAX_RATE_LIMIT);
        }
        this.rateLimitRequests = requests;
    }

    public int getRetentionDays() {
        return retentionDays;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
