package dev.hookdock.destinations;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DestinationRepository extends JpaRepository<Destination, UUID> {

    List<Destination> findByProjectIdOrderByNameAsc(UUID projectId);
}
