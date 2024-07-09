package dev.hookdock.delivery;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DeliveryAttemptRepository extends JpaRepository<DeliveryAttempt, UUID> {

    List<DeliveryAttempt> findByCapturedWebhookIdOrderByAttemptNumberAsc(UUID capturedWebhookId);

    @Query("select a from DeliveryAttempt a where a.capturedWebhookId in :eventIds and a.attemptNumber = "
            + "(select max(b.attemptNumber) from DeliveryAttempt b where b.capturedWebhookId = a.capturedWebhookId)")
    List<DeliveryAttempt> findLatestForEvents(Collection<UUID> eventIds);

    @Query("select coalesce(max(a.attemptNumber), 0) from DeliveryAttempt a where a.capturedWebhookId = :eventId")
    int maxAttemptNumber(UUID eventId);

    @Query("select a.id from DeliveryAttempt a where a.status = dev.hookdock.delivery.DeliveryStatus.PENDING "
            + "order by a.queuedAt asc")
    List<UUID> findPendingIds(Pageable page);

    /**
     * Atomically moves an attempt from PENDING to IN_PROGRESS. Returns 1 if this
     * worker won the claim, 0 if another worker got there first.
     */
    @Modifying
    @Query("update DeliveryAttempt a set a.status = dev.hookdock.delivery.DeliveryStatus.IN_PROGRESS, "
            + "a.startedAt = :now, a.leaseOwner = :owner, a.leaseExpiresAt = :leaseUntil "
            + "where a.id = :id and a.status = dev.hookdock.delivery.DeliveryStatus.PENDING")
    int claim(UUID id, String owner, Instant now, Instant leaseUntil);
}
