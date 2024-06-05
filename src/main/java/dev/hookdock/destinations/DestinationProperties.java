package dev.hookdock.destinations;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Outbound network policy.
 *
 * @param allowPrivateNetworks allow loopback/private targets. Handy for local
 *                             development (forwarding to localhost), must stay
 *                             off anywhere HookDock is reachable by others.
 * @param allowedPorts         ports a destination may use when private networks
 *                             are not allowed
 */
@ConfigurationProperties(prefix = "hookdock.destinations")
public record DestinationProperties(boolean allowPrivateNetworks, List<Integer> allowedPorts) {

    public DestinationProperties {
        if (allowedPorts == null || allowedPorts.isEmpty()) {
            allowedPorts = List.of(80, 443, 8080, 8443);
        }
    }
}
