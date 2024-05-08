package dev.hookdock.projects;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface ProjectRepository extends JpaRepository<Project, UUID> {

    List<Project> findByOwnerIdOrderByCreatedAtDesc(UUID ownerId);

    Optional<Project> findByIdAndOwnerId(UUID id, UUID ownerId);

    @Modifying
    @Query("update Project p set p.ownerId = :ownerId where p.ownerId is null")
    int assignOwnerWhereMissing(UUID ownerId);
}
