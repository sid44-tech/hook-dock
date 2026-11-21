package dev.hookdock.ingestion;

import java.time.Instant;
import java.util.UUID;

/**
 * An inbound request that reached a real endpoint but was not stored.
 *
 * @param limit the endpoint's rate limit when the request was rejected
 */
public record Rejection(UUID endpointId, Instant rejectedAt, String method, RejectionReason reason, int limit) {
}
