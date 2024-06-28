package dev.hookdock.events;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.hookdock.common.NotFoundException;
import dev.hookdock.delivery.DeliveryAttempt;
import dev.hookdock.delivery.DeliveryService;
import dev.hookdock.delivery.DeliveryStatus;
import dev.hookdock.endpoints.EndpointService;
import dev.hookdock.endpoints.WebhookEndpoint;

@Service
public class EventService {

    private final CapturedWebhookRepository events;
    private final EndpointService endpointService;
    private final DeliveryService deliveryService;

    public EventService(CapturedWebhookRepository events, EndpointService endpointService,
            DeliveryService deliveryService) {
        this.events = events;
        this.endpointService = endpointService;
        this.deliveryService = deliveryService;
    }

    @Transactional(readOnly = true)
    public List<EventRow> listForProject(UUID projectId) {
        Map<UUID, String> endpointNames = endpointService.listForProject(projectId).stream()
                .collect(Collectors.toMap(WebhookEndpoint::getId, WebhookEndpoint::getName));
        if (endpointNames.isEmpty()) {
            return List.of();
        }
        return events.findByEndpointIdInOrderByReceivedAtDesc(endpointNames.keySet()).stream()
                .map(e -> new EventRow(e, endpointNames.get(e.getEndpointId()), latestStatus(e.getId())))
                .toList();
    }

    private DeliveryStatus latestStatus(UUID eventId) {
        List<DeliveryAttempt> attempts = deliveryService.attemptsFor(eventId);
        return attempts.isEmpty() ? null : attempts.get(attempts.size() - 1).getStatus();
    }

    @Transactional(readOnly = true)
    public CapturedWebhook get(UUID eventId) {
        return events.findById(eventId).orElseThrow(() -> new NotFoundException("Event not found"));
    }
}
