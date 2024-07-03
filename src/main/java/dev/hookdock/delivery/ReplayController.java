package dev.hookdock.delivery;

import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import dev.hookdock.destinations.Destination;
import dev.hookdock.destinations.DestinationService;
import dev.hookdock.endpoints.EndpointService;
import dev.hookdock.endpoints.WebhookEndpoint;
import dev.hookdock.events.CapturedWebhook;
import dev.hookdock.events.EventService;
import dev.hookdock.projects.ProjectService;
import dev.hookdock.security.CurrentUser;

/**
 * Queues a replay of a captured event to one of the project's approved
 * destinations. The request only queues the attempt - the worker sends it - so
 * the UI reports "queued", not "delivered".
 */
@Controller
public class ReplayController {

    private final EventService eventService;
    private final EndpointService endpointService;
    private final ProjectService projectService;
    private final DestinationService destinationService;
    private final DeliveryService deliveryService;
    private final CurrentUser currentUser;

    public ReplayController(EventService eventService, EndpointService endpointService,
            ProjectService projectService, DestinationService destinationService, DeliveryService deliveryService,
            CurrentUser currentUser) {
        this.eventService = eventService;
        this.endpointService = endpointService;
        this.projectService = projectService;
        this.destinationService = destinationService;
        this.deliveryService = deliveryService;
        this.currentUser = currentUser;
    }

    @PostMapping("/events/{eventId}/replays")
    public String replay(@PathVariable UUID eventId, @RequestParam UUID destinationId,
            @RequestParam(defaultValue = "false") boolean confirm, RedirectAttributes redirect) {
        CapturedWebhook event = eventService.get(eventId);
        WebhookEndpoint endpoint = endpointService.get(event.getEndpointId());
        projectService.getProject(endpoint.getProjectId());

        String back = "redirect:/events/" + eventId;
        if (!confirm) {
            redirect.addFlashAttribute("error", "Confirm that you understand a replay may repeat side effects.");
            return back;
        }
        Destination destination = destinationService.get(destinationId);
        if (!destination.getProjectId().equals(endpoint.getProjectId()) || !destination.isEnabled()) {
            redirect.addFlashAttribute("error", "Pick an enabled destination from this project.");
            return back;
        }

        DeliveryAttempt attempt = deliveryService.enqueueReplay(eventId, destinationId, currentUser.id());
        redirect.addFlashAttribute("message",
                "Replay queued as attempt #" + attempt.getAttemptNumber() + " to " + destination.getName() + ".");
        return back;
    }
}
