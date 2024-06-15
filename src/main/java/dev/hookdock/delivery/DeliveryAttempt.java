package dev.hookdock.delivery;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import dev.hookdock.events.HeadersJsonConverter;

/**
 * One try at sending a captured webhook to a destination. Every forward, retry
 * and replay gets its own row.
 */
@Entity
@Table(name = "delivery_attempt")
public class DeliveryAttempt {

    @Id
    private UUID id;

    @Column(name = "captured_webhook_id", nullable = false)
    private UUID capturedWebhookId;

    @Column(name = "destination_id", nullable = false)
    private UUID destinationId;

    @Column(name = "attempt_number", nullable = false)
    private int attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private TriggerType triggerType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DeliveryStatus status;

    @Column(name = "queued_at", nullable = false)
    private Instant queuedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "duration_ms")
    private Long durationMs;

    @Column(name = "http_status")
    private Integer httpStatus;

    @Convert(converter = HeadersJsonConverter.class)
    @Column(name = "response_headers", columnDefinition = "text")
    private Map<String, List<String>> responseHeaders;

    @Column(name = "response_body_excerpt", columnDefinition = "text")
    private String responseBodyExcerpt;

    @Enumerated(EnumType.STRING)
    @Column(name = "error_category", length = 30)
    private ErrorCategory errorCategory;

    @Column(name = "error_message", length = 1000)
    private String errorMessage;

    @Column(name = "requested_by_user_id")
    private UUID requestedByUserId;

    @Column(name = "lease_owner", length = 100)
    private String leaseOwner;

    @Column(name = "lease_expires_at")
    private Instant leaseExpiresAt;

    protected DeliveryAttempt() {
    }

    public DeliveryAttempt(UUID capturedWebhookId, UUID destinationId, int attemptNumber, TriggerType triggerType,
            UUID requestedByUserId, Instant queuedAt) {
        this.id = UUID.randomUUID();
        this.capturedWebhookId = capturedWebhookId;
        this.destinationId = destinationId;
        this.attemptNumber = attemptNumber;
        this.triggerType = triggerType;
        this.requestedByUserId = requestedByUserId;
        this.status = DeliveryStatus.PENDING;
        this.queuedAt = queuedAt;
    }

    public void succeed(int httpStatus, Map<String, List<String>> headers, String bodyExcerpt, Instant now) {
        this.status = DeliveryStatus.SUCCEEDED;
        this.httpStatus = httpStatus;
        this.responseHeaders = headers;
        this.responseBodyExcerpt = bodyExcerpt;
        finish(now);
    }

    /** The exchange completed but the target returned a non-2xx status. */
    public void failWithResponse(int httpStatus, Map<String, List<String>> headers, String bodyExcerpt,
            Instant now) {
        this.status = DeliveryStatus.FAILED;
        this.httpStatus = httpStatus;
        this.responseHeaders = headers;
        this.responseBodyExcerpt = bodyExcerpt;
        this.errorCategory = ErrorCategory.HTTP_STATUS;
        this.errorMessage = "Target responded with HTTP " + httpStatus;
        finish(now);
    }

    public void fail(ErrorCategory category, String message, Instant now) {
        this.status = DeliveryStatus.FAILED;
        this.errorCategory = category;
        this.errorMessage = truncate(message, 1000);
        finish(now);
    }

    public void block(ErrorCategory category, String message, Instant now) {
        this.status = DeliveryStatus.BLOCKED;
        this.errorCategory = category;
        this.errorMessage = truncate(message, 1000);
        finish(now);
    }

    private void finish(Instant now) {
        this.completedAt = now;
        if (startedAt != null) {
            this.durationMs = now.toEpochMilli() - startedAt.toEpochMilli();
        }
        this.leaseOwner = null;
        this.leaseExpiresAt = null;
    }

    private static String truncate(String value, int max) {
        if (value == null || value.length() <= max) {
            return value;
        }
        return value.substring(0, max);
    }

    public UUID getId() {
        return id;
    }

    public UUID getCapturedWebhookId() {
        return capturedWebhookId;
    }

    public UUID getDestinationId() {
        return destinationId;
    }

    public int getAttemptNumber() {
        return attemptNumber;
    }

    public TriggerType getTriggerType() {
        return triggerType;
    }

    public DeliveryStatus getStatus() {
        return status;
    }

    public Instant getQueuedAt() {
        return queuedAt;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public Map<String, List<String>> getResponseHeaders() {
        return responseHeaders;
    }

    public String getResponseBodyExcerpt() {
        return responseBodyExcerpt;
    }

    public ErrorCategory getErrorCategory() {
        return errorCategory;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public UUID getRequestedByUserId() {
        return requestedByUserId;
    }

    public String getLeaseOwner() {
        return leaseOwner;
    }

    public Instant getLeaseExpiresAt() {
        return leaseExpiresAt;
    }
}
