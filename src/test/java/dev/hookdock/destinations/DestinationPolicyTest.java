package dev.hookdock.destinations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DestinationPolicyTest {

    private static final Map<String, List<String>> DNS = Map.of(
            "public.example", List.of("93.184.216.34"),
            "mixed.example", List.of("93.184.216.34", "10.0.0.5"),
            "rebind.example", List.of("127.0.0.1"));

    private final HostResolver fakeDns = host -> {
        List<String> ips = DNS.get(host);
        if (ips == null) {
            // Literal IPs resolve to themselves without a lookup.
            if (host.matches("[0-9.]+") || host.contains(":")) {
                return List.of(InetAddress.getByName(host));
            }
            throw new UnknownHostException(host);
        }
        return ips.stream().map(DestinationPolicyTest::addr).toList();
    };

    private final DestinationPolicy policy = new DestinationPolicy(new DestinationProperties(false, null), fakeDns);

    @Test
    void acceptsPublicHttpsUrl() {
        assertThat(policy.validate("https://public.example/hooks").toString())
                .isEqualTo("https://public.example/hooks");
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "ftp://public.example/",
            "file:///etc/passwd",
            "gopher://public.example/",
            "https://user:pass@public.example/",
            "https://public.example:22/",
            "https://public.example/#frag",
            "not a url",
            "/relative/path",
            ""
    })
    void rejectsBadUrls(String url) {
        assertThatThrownBy(() -> policy.validate(url)).isInstanceOf(DestinationRejectedException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1/",
            "http://10.1.2.3/",
            "http://172.16.0.1/",
            "http://192.168.1.1/",
            "http://169.254.169.254/latest/meta-data",
            "http://0.0.0.0/",
            "http://100.64.0.1/",
            "http://[::1]/",
            "http://[fd00::1]/",
            "http://[fe80::1]/",
            "http://[::ffff:127.0.0.1]/",
            "http://[64:ff9b::a00:1]/",
            "http://rebind.example/"
    })
    void rejectsNonPublicAddresses(String url) {
        assertThatThrownBy(() -> policy.validate(url))
                .isInstanceOf(DestinationRejectedException.class)
                .hasMessageContaining("non-public");
    }

    @Test
    void rejectsWhenAnyResolvedAddressIsPrivate() {
        assertThatThrownBy(() -> policy.validate("https://mixed.example/"))
                .isInstanceOf(DestinationRejectedException.class);
    }

    @Test
    void rejectsUnresolvableHosts() {
        assertThatThrownBy(() -> policy.validate("https://nope.example/"))
                .hasMessageContaining("could not be resolved");
    }

    @Test
    void privateNetworksCanBeAllowedForLocalDev() {
        DestinationPolicy local = new DestinationPolicy(new DestinationProperties(true, null), fakeDns);
        assertThat(local.validate("http://127.0.0.1:9000/hook")).isNotNull();
    }

    private static InetAddress addr(String ip) {
        try {
            return InetAddress.getByName(ip);
        } catch (UnknownHostException e) {
            throw new IllegalArgumentException(e);
        }
    }
}
