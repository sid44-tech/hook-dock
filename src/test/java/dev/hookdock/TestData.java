package dev.hookdock;

import java.time.Instant;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.hookdock.endpoints.CreatedEndpoint;
import dev.hookdock.endpoints.EndpointService;
import dev.hookdock.projects.Project;
import dev.hookdock.projects.ProjectRepository;
import dev.hookdock.security.AppUser;
import dev.hookdock.security.AppUserRepository;

/**
 * Helpers for setting up fixtures without going through the current-user
 * scoped services.
 */
@Component
public class TestData {

    private final AppUserRepository users;
    private final ProjectRepository projects;
    private final EndpointService endpoints;

    public TestData(AppUserRepository users, ProjectRepository projects, EndpointService endpoints) {
        this.users = users;
        this.projects = projects;
        this.endpoints = endpoints;
    }

    @Transactional
    public AppUser user(String username) {
        return users.findByUsername(username)
                .orElseGet(() -> users.save(new AppUser(username, "{noop}not-used", Instant.now())));
    }

    @Transactional
    public Project project(AppUser owner, String name) {
        return projects.save(new Project(owner.getId(), name, null, Instant.now()));
    }

    public CreatedEndpoint endpoint(Project project, String name) {
        return endpoints.create(project.getId(), name);
    }
}
