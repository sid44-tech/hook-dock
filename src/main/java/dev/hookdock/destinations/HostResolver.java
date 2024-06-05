package dev.hookdock.destinations;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;

/**
 * Seam over DNS so the destination policy can be tested without real lookups.
 */
@FunctionalInterface
public interface HostResolver {

    List<InetAddress> resolve(String host) throws UnknownHostException;

    static HostResolver system() {
        return host -> List.of(InetAddress.getAllByName(host));
    }
}
