package dev.hookdock.projects;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/projects")
public class ProjectController {

    private final ProjectService projectService;

    public ProjectController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("projects", projectService.listProjects());
        model.addAttribute("form", new ProjectForm("", ""));
        return "projects/list";
    }

    @PostMapping
    public String create(@Valid @ModelAttribute("form") ProjectForm form, BindingResult result, Model model) {
        if (result.hasErrors()) {
            model.addAttribute("projects", projectService.listProjects());
            return "projects/list";
        }
        Project project = projectService.createProject(form.name(), form.description());
        return "redirect:/projects/" + project.getId();
    }

    @GetMapping("/{projectId}")
    public String detail(@PathVariable UUID projectId, Model model) {
        model.addAttribute("project", projectService.getProject(projectId));
        return "projects/detail";
    }
}
