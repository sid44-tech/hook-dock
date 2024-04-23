package dev.hookdock.events;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.hookdock.common.NotFoundException;
import dev.hookdock.endpoints.EndpointService;
import dev.hookdock.endpoints.WebhookEndpoint;

@Service
public class EventService {

    private final CapturedWebhookRepository events;
    private final EndpointService endpointService;

    public EventService(CapturedWebhookRepository events, EndpointService endpointService) {
        this.events = events;
        this.endpointService = endpointService;
    }

    @Transactional(readOnly = true)
    public List<EventRow> listForProject(UUID projectId) {
        Map<UUID, String> endpointNames = endpointService.listForProject(projectId).stream()
                .collect(Collectors.toMap(WebhookEndpoint::getId, WebhookEndpoint::getName));
        if (endpointNames.isEmpty()) {
            return List.of();
        }
        return events.findByEndpointIdInOrderByReceivedAtDesc(endpointNames.keySet()).stream()
                .map(e -> new EventRow(e, endpointNames.get(e.getEndpointId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public CapturedWebhook get(UUID eventId) {
        return events.findById(eventId).orElseThrow(() -> new NotFoundException("Event not found"));
    }
}
