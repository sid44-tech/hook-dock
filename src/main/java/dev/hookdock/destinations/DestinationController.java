package dev.hookdock.destinations;

import java.util.UUID;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import dev.hookdock.projects.ProjectService;

@Controller
public class DestinationController {

    private final DestinationService destinationService;
    private final ProjectService projectService;

    public DestinationController(DestinationService destinationService, ProjectService projectService) {
        this.destinationService = destinationService;
        this.projectService = projectService;
    }

    @PostMapping("/projects/{projectId}/destinations")
    public String create(@PathVariable UUID projectId, @RequestParam String name, @RequestParam String url,
            RedirectAttributes redirect) {
        projectService.getProject(projectId);
        try {
            destinationService.create(projectId, name, url);
        } catch (DestinationRejectedException e) {
            redirect.addFlashAttribute("error", "Destination rejected: " + e.getMessage());
        }
        return "redirect:/projects/" + projectId;
    }

    @PostMapping("/destinations/{destinationId}/enabled")
    public String toggle(@PathVariable UUID destinationId, @RequestParam boolean enabled) {
        Destination destination = destinationService.get(destinationId);
        projectService.getProject(destination.getProjectId());
        destinationService.setEnabled(destinationId, enabled);
        return "redirect:/projects/" + destination.getProjectId();
    }
}
