package dev.hookdock.destinations;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Decides whether HookDock may send requests to a URL. Applied when a
 * destination is saved and again right before every delivery, since DNS can
 * change in between.
 * <p>
 * This is a URL/DNS level check only. Deployments should still restrict egress
 * at the network level - see the security section of the README.
 */
@Component
public class DestinationPolicy {

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");

    private final DestinationProperties properties;
    private final HostResolver resolver;

    @Autowired
    public DestinationPolicy(DestinationProperties properties) {
        this(properties, HostResolver.system());
    }

    DestinationPolicy(DestinationProperties properties, HostResolver resolver) {
        this.properties = properties;
        this.resolver = resolver;
    }

    public URI validate(String url) {
        URI uri = parse(url);
        String host = uri.getHost();

        List<InetAddress> addresses;
        try {
            addresses = resolver.resolve(host);
        } catch (UnknownHostException e) {
            throw new DestinationRejectedException("Host could not be resolved: " + host);
        }
        if (addresses.isEmpty()) {
            throw new DestinationRejectedException("Host could not be resolved: " + host);
        }
        if (!properties.allowPrivateNetworks()) {
            // Every address must be public, not just the first one.
            for (InetAddress address : addresses) {
                if (isNonPublic(address)) {
                    throw new DestinationRejectedException(
                            "Destination resolves to a non-public address (" + address.getHostAddress() + ")");
                }
            }
        }
        return uri;
    }

    private URI parse(String url) {
        if (url == null || url.isBlank()) {
            throw new DestinationRejectedException("URL is required");
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw new DestinationRejectedException("Not a valid URL");
        }
        String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme)) {
            throw new DestinationRejectedException("Only http and https URLs are allowed");
        }
        if (uri.getRawUserInfo() != null) {
            throw new DestinationRejectedException("Credentials in the URL are not allowed");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new DestinationRejectedException("URL must include a host");
        }
        if (uri.getRawFragment() != null) {
            throw new DestinationRejectedException("URL fragments are not allowed");
        }
        int port = effectivePort(uri);
        if (!properties.allowPrivateNetworks() && !properties.allowedPorts().contains(port)) {
            throw new DestinationRejectedException("Port " + port + " is not allowed");
        }
        return uri;
    }

    static int effectivePort(URI uri) {
        if (uri.getPort() != -1) {
            return uri.getPort();
        }
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }

    static boolean isNonPublic(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress();
    }
}
