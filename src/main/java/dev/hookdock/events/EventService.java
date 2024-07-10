package dev.hookdock.events;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.hookdock.common.NotFoundException;
import dev.hookdock.delivery.DeliveryService;
import dev.hookdock.delivery.DeliveryStatus;
import dev.hookdock.endpoints.EndpointService;
import dev.hookdock.endpoints.WebhookEndpoint;

@Service
public class EventService {

    public static final int PAGE_SIZE = 50;

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
    public Page<EventRow> listForProject(UUID projectId, int page) {
        PageRequest request = PageRequest.of(Math.max(page, 0), PAGE_SIZE, Sort.by(Sort.Direction.DESC, "receivedAt"));
        Map<UUID, String> endpointNames = endpointService.listForProject(projectId).stream()
                .collect(Collectors.toMap(WebhookEndpoint::getId, WebhookEndpoint::getName));
        if (endpointNames.isEmpty()) {
            return Page.empty(request);
        }
        Page<CapturedWebhook> result = events.findByEndpointIdIn(endpointNames.keySet(), request);

        // One query for all delivery statuses on the page instead of one per row.
        List<UUID> ids = result.getContent().stream().map(CapturedWebhook::getId).toList();
        Map<UUID, DeliveryStatus> statuses = deliveryService.latestStatuses(ids);

        return result.map(e -> new EventRow(e, endpointNames.get(e.getEndpointId()), statuses.get(e.getId())));
    }

    @Transactional(readOnly = true)
    public long countForEndpoints(List<UUID> endpointIds) {
        return endpointIds.isEmpty() ? 0 : events.countByEndpointIdIn(endpointIds);
    }

    @Transactional(readOnly = true)
    public List<CapturedWebhook> recentForEndpoints(List<UUID> endpointIds, int limit) {
        if (endpointIds.isEmpty()) {
            return List.of();
        }
        return events.findByEndpointIdIn(endpointIds,
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "receivedAt"))).getContent();
    }

    @Transactional(readOnly = true)
    public CapturedWebhook get(UUID eventId) {
        return events.findById(eventId).orElseThrow(() -> new NotFoundException("Event not found"));
    }
}
