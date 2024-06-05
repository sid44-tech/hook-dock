package dev.hookdock.destinations;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import dev.hookdock.common.NotFoundException;

@Service
public class DestinationService {

    private final DestinationRepository destinations;
    private final DestinationPolicy policy;
    private final Clock clock;

    public DestinationService(DestinationRepository destinations, DestinationPolicy policy, Clock clock) {
        this.destinations = destinations;
        this.policy = policy;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<Destination> listForProject(UUID projectId) {
        return destinations.findByProjectIdOrderByNameAsc(projectId);
    }

    @Transactional(readOnly = true)
    public Destination get(UUID id) {
        return destinations.findById(id).orElseThrow(() -> new NotFoundException("Destination not found"));
    }

    @Transactional
    public Destination create(UUID projectId, String name, String url) {
        if (name == null || name.isBlank() || name.length() > 100) {
            throw new DestinationRejectedException("Name is required (max 100 characters)");
        }
        String normalized = policy.validate(url).toString();
        return destinations.save(new Destination(projectId, name.trim(), normalized, clock.instant()));
    }

    @Transactional
    public void setEnabled(UUID id, boolean enabled) {
        get(id).setEnabled(enabled, clock.instant());
    }
}
