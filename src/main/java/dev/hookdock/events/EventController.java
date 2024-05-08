package dev.hookdock.events;

import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import dev.hookdock.endpoints.EndpointService;
import dev.hookdock.endpoints.WebhookEndpoint;
import dev.hookdock.projects.ProjectService;

@Controller
public class EventController {

    private final EventService eventService;
    private final EndpointService endpointService;
    private final ProjectService projectService;
    private final PayloadFormatter payloadFormatter;

    public EventController(EventService eventService, EndpointService endpointService,
            ProjectService projectService, PayloadFormatter payloadFormatter) {
        this.eventService = eventService;
        this.endpointService = endpointService;
        this.projectService = projectService;
        this.payloadFormatter = payloadFormatter;
    }

    @GetMapping("/projects/{projectId}/events")
    public String list(@PathVariable UUID projectId, Model model) {
        model.addAttribute("project", projectService.getProject(projectId));
        model.addAttribute("rows", eventService.listForProject(projectId));
        return "events/list";
    }

    @GetMapping("/events/{eventId}")
    public String detail(@PathVariable UUID eventId, Model model) {
        CapturedWebhook event = eventService.get(eventId);
        WebhookEndpoint endpoint = endpointService.get(event.getEndpointId());
        model.addAttribute("project", projectService.getProject(endpoint.getProjectId()));
        model.addAttribute("event", event);
        model.addAttribute("endpoint", endpoint);
        model.addAttribute("payload", payloadFormatter.format(event.getRequestBody(), event.getContentType()));
        return "events/detail";
    }
}
