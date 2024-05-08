package dev.hookdock.projects;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.hookdock.common.NotFoundException;
import dev.hookdock.security.CurrentUser;

/**
 * All project lookups are scoped to the signed-in user. Other modules should go
 * through {@link #getProject(UUID)} before touching anything that belongs to a
 * project, so access checks live in one place.
 */
@Service
public class ProjectService {

    private final ProjectRepository projects;
    private final CurrentUser currentUser;
    private final Clock clock;

    public ProjectService(ProjectRepository projects, CurrentUser currentUser, Clock clock) {
        this.projects = projects;
        this.currentUser = currentUser;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Project> listProjects() {
        return projects.findByOwnerIdOrderByCreatedAtDesc(currentUser.id());
    }

    /**
     * Returns the project if the current user may access it. Responds 404 rather
     * than 403 so we don't confirm which IDs exist.
     */
    @Transactional(readOnly = true)
    public Project getProject(UUID id) {
        return projects.findByIdAndOwnerId(id, currentUser.id())
                .orElseThrow(() -> new NotFoundException("Project not found"));
    }

    @Transactional
    public Project createProject(String name, String description) {
        return projects.save(new Project(currentUser.id(), name.trim(), blankToNull(description), clock.instant()));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
