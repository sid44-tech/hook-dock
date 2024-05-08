package dev.hookdock.endpoints;

import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import dev.hookdock.projects.ProjectService;

@Controller
public class EndpointController {

    private final EndpointService endpointService;
    private final ProjectService projectService;

    public EndpointController(EndpointService endpointService, ProjectService projectService) {
        this.endpointService = endpointService;
        this.projectService = projectService;
    }

    @PostMapping("/projects/{projectId}/endpoints")
    public String create(@PathVariable UUID projectId, @RequestParam String name, RedirectAttributes redirect) {
        projectService.getProject(projectId);
        if (name == null || name.isBlank() || name.length() > 100) {
            redirect.addFlashAttribute("error", "Endpoint name is required (max 100 characters).");
            return "redirect:/projects/" + projectId;
        }
        CreatedEndpoint created = endpointService.create(projectId, name);
        String receiveUrl = ServletUriComponentsBuilder.fromCurrentContextPath()
                .path("/in/{token}")
                .buildAndExpand(created.token())
                .toUriString();
        redirect.addFlashAttribute("newEndpointName", created.endpoint().getName());
        redirect.addFlashAttribute("newReceiveUrl", receiveUrl);
        return "redirect:/projects/" + projectId;
    }

    @PostMapping("/endpoints/{endpointId}/enabled")
    public String toggle(@PathVariable UUID endpointId, @RequestParam boolean enabled) {
        WebhookEndpoint endpoint = endpointService.get(endpointId);
        projectService.getProject(endpoint.getProjectId());
        endpointService.setEnabled(endpointId, enabled);
        return "redirect:/projects/" + endpoint.getProjectId();
    }
}
