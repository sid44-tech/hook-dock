package dev.hookdock.events;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface CapturedWebhookRepository extends JpaRepository<CapturedWebhook, UUID> {
}
