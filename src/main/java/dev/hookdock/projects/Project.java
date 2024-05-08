package dev.hookdock.projects;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "project")
public class Project {

    @Id
    private UUID id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "owner_id")
    private UUID ownerId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Project() {
    }

    public Project(UUID ownerId, String name, String description, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.name = name;
        this.description = description;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
