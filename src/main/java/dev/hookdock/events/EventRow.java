package dev.hookdock.events;

import dev.hookdock.delivery.DeliveryStatus;

/**
 * @param latestDelivery status of the most recent delivery attempt, or null if
 *                       the event was only captured
 */
public record EventRow(CapturedWebhook event, String endpointName, DeliveryStatus latestDelivery) {
}
