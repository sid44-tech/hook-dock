package dev.hookdock.delivery;

import java.net.URI;
import java.util.List;
import java.util.Map;

record OutboundRequest(String method, URI uri, Map<String, List<String>> headers, byte[] body) {
}
