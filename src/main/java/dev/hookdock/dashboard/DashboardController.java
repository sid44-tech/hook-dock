package dev.hookdock.dashboard;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import dev.hookdock.delivery.DeliveryService;
import dev.hookdock.endpoints.EndpointService;
import dev.hookdock.endpoints.WebhookEndpoint;
import dev.hookdock.events.EventService;
import dev.hookdock.ingestion.RecentRejections;
import dev.hookdock.projects.Project;
import dev.hookdock.projects.ProjectService;
import dev.hookdock.projects.RateLimitView;
import dev.hookdock.ratelimit.RateLimitProperties;

@Controller
public class DashboardController {

    private static final int RECENT_LIMIT = 10;

    private final ProjectService projectService;
    private final EndpointService endpointService;
    private final EventService eventService;
    private final DeliveryService deliveryService;
    private final RecentRejections recentRejections;
    private final RateLimitView rateLimit;

    public DashboardController(ProjectService projectService, EndpointService endpointService,
            EventService eventService, DeliveryService deliveryService, RecentRejections recentRejections,
            RateLimitProperties rateLimitProperties) {
        this.projectService = projectService;
        this.endpointService = endpointService;
        this.eventService = eventService;
        this.deliveryService = deliveryService;
        this.recentRejections = recentRejections;
        this.rateLimit = RateLimitView.from(rateLimitProperties);
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        List<Project> projects = projectService.listProjects();
        List<WebhookEndpoint> endpoints = endpointService.listForProjects(
                projects.stream().map(Project::getId).toList());
        List<UUID> endpointIds = endpoints.stream().map(WebhookEndpoint::getId).toList();

        model.addAttribute("projects", projects);
        model.addAttribute("endpointCount", endpoints.size());
        model.addAttribute("eventCount", eventService.countForEndpoints(endpointIds));
        model.addAttribute("recentEvents", eventService.recentForEndpoints(endpointIds, RECENT_LIMIT));
        // endpointIds only holds the signed-in user's endpoints, so this never shows anyone else's.
        model.addAttribute("recentRejections", recentRejections.recentForEndpoints(endpointIds, RECENT_LIMIT));
        model.addAttribute("rejectedLast24h", recentRejections.countInWindow(endpointIds));
        model.addAttribute("rateLimit", rateLimit);
        model.addAttribute("recentFailures", deliveryService.recentFailures(endpointIds, RECENT_LIMIT));
        model.addAttribute("endpointsById", endpoints.stream()
                .collect(Collectors.toMap(WebhookEndpoint::getId, Function.identity())));
        model.addAttribute("projectsById", projects.stream()
                .collect(Collectors.toMap(Project::getId, Function.identity())));
        return "dashboard";
    }
}
