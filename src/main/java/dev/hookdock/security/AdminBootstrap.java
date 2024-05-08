package dev.hookdock.security;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import dev.hookdock.projects.ProjectRepository;

@Component
public class AdminBootstrap {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final AppUserRepository users;
    private final ProjectRepository projects;
    private final AdminProperties properties;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public AdminBootstrap(AppUserRepository users, ProjectRepository projects, AdminProperties properties,
            PasswordEncoder passwordEncoder, Clock clock) {
        this.users = users;
        this.projects = projects;
        this.properties = properties;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onStartup() {
        if (users.count() == 0) {
            createAdmin();
        }
        users.findAll().stream()
                .min((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()))
                .ifPresent(owner -> {
                    int updated = projects.assignOwnerWhereMissing(owner.getId());
                    if (updated > 0) {
                        log.info("Assigned {} unowned project(s) to '{}'", updated, owner.getUsername());
                    }
                });
    }

    private void createAdmin() {
        if (isBlank(properties.username()) || isBlank(properties.password())) {
            log.warn("No users exist and HOOKDOCK_ADMIN_USERNAME / HOOKDOCK_ADMIN_PASSWORD are not set. "
                    + "Nobody will be able to sign in.");
            return;
        }
        users.save(new AppUser(properties.username(), passwordEncoder.encode(properties.password()), clock.instant()));
        log.info("Created initial admin user '{}'", properties.username());
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
