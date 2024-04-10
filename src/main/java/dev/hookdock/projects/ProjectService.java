package dev.hookdock.projects;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.hookdock.common.NotFoundException;

@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final Clock clock;

    public ProjectService(ProjectRepository projects, Clock clock) {
        this.projects = projects;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Project> listProjects() {
        return projects.findAllByOrderByCreatedAtDesc();
    }

    @Transactional(readOnly = true)
    public Project getProject(UUID id) {
        return projects.findById(id).orElseThrow(() -> new NotFoundException("Project not found"));
    }

    @Transactional
    public Project createProject(String name, String description) {
        return projects.save(new Project(name.trim(), blankToNull(description), clock.instant()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
