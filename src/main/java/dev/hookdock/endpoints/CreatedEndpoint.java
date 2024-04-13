package dev.hookdock.endpoints;

/**
 * Returned once on creation so the UI can show the plaintext token a single time.
 */
public record CreatedEndpoint(WebhookEndpoint endpoint, String token) {
}
