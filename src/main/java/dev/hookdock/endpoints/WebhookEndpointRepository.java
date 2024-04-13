package dev.hookdock.endpoints;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WebhookEndpointRepository extends JpaRepository<WebhookEndpoint, UUID> {

    List<WebhookEndpoint> findByProjectIdOrderByCreatedAtAsc(UUID projectId);

    Optional<WebhookEndpoint> findByTokenHash(String tokenHash);
}
