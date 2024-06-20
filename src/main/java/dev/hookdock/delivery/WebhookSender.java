package dev.hookdock.delivery;

import java.io.IOException;
import java.io.InputStream;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * Sends a single HTTP request. Redirects are never followed: a redirect could
 * point anywhere, including addresses the destination policy would reject.
 */
@Component
class WebhookSender {

    private final HttpClient client;
    private final DeliveryProperties properties;

    WebhookSender(DeliveryProperties properties) {
        this.properties = properties;
        this.client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(properties.connectTimeout())
                .build();
    }

    OutboundResponse send(OutboundRequest request) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(request.uri())
                .timeout(properties.requestTimeout())
                .method(request.method(), HttpRequest.BodyPublishers.ofByteArray(request.body()));
        request.headers().forEach((name, values) -> values.forEach(v -> builder.header(name, v)));

        HttpResponse<InputStream> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofInputStream());
        return new OutboundResponse(response.statusCode(), headers(response), excerpt(response.body()));
    }

    private String excerpt(InputStream body) throws IOException {
        try (body) {
            byte[] bytes = body.readNBytes((int) properties.responseExcerptSize().toBytes());
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }

    private static Map<String, List<String>> headers(HttpResponse<?> response) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        response.headers().map().forEach((name, values) -> {
            if (!name.startsWith(":")) {
                headers.put(name, values);
            }
        });
        return headers;
    }
}
