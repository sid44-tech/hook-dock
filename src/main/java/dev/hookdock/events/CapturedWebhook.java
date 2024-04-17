package dev.hookdock.events;

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

/**
 * An inbound request exactly as we received it. Never modified after capture;
 * replays and forwards create delivery attempts instead.
 */
@Entity
@Table(name = "captured_webhook")
public class CapturedWebhook {

    @Id
    private UUID id;

    @Column(name = "endpoint_id", nullable = false)
    private UUID endpointId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(name = "raw_path", nullable = false, length = 2048)
    private String rawPath;

    @Column(name = "query_string", length = 4096)
    private String queryString;

    @Convert(converter = HeadersJsonConverter.class)
    @Column(name = "request_headers", nullable = false, columnDefinition = "text")
    private Map<String, List<String>> requestHeaders;

    @Column(name = "request_body")
    private byte[] requestBody;

    @Column(name = "content_type")
    private String contentType;

    @Column(name = "body_size", nullable = false)
    private int bodySize;

    @Column(length = 50)
    private String provider;

    @Column(name = "provider_event_id")
    private String providerEventId;

    @Enumerated(EnumType.STRING)
    @Column(name = "capture_status", nullable = false, length = 20)
    private CaptureStatus captureStatus;

    protected CapturedWebhook() {
    }

    public CapturedWebhook(UUID endpointId, Instant receivedAt, String method, String rawPath, String queryString,
            Map<String, List<String>> requestHeaders, byte[] requestBody, String contentType,
            String provider, String providerEventId) {
        this.id = UUID.randomUUID();
        this.endpointId = endpointId;
        this.receivedAt = receivedAt;
        this.method = method;
        this.rawPath = rawPath;
        this.queryString = queryString;
        this.requestHeaders = requestHeaders;
        this.requestBody = requestBody;
        this.bodySize = requestBody == null ? 0 : requestBody.length;
        this.contentType = contentType;
        this.provider = provider;
        this.providerEventId = providerEventId;
        this.captureStatus = CaptureStatus.CAPTURED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getEndpointId() {
        return endpointId;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public String getMethod() {
        return method;
    }

    public String getRawPath() {
        return rawPath;
    }

    public String getQueryString() {
        return queryString;
    }

    public Map<String, List<String>> getRequestHeaders() {
        return requestHeaders;
    }

    public byte[] getRequestBody() {
        return requestBody;
    }

    public String getContentType() {
        return contentType;
    }

    public int getBodySize() {
        return bodySize;
    }

    public String getProvider() {
        return provider;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public CaptureStatus getCaptureStatus() {
        return captureStatus;
    }
}
