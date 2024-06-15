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

    public int getRetentionDays() {
        return retentionDays;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
