package dev.hookdock.events;

import java.util.Collection;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CapturedWebhookRepository extends JpaRepository<CapturedWebhook, UUID> {

    Page<CapturedWebhook> findByEndpointIdIn(Collection<UUID> endpointIds, Pageable pageable);

    long countByEndpointIdIn(Collection<UUID> endpointIds);
}
