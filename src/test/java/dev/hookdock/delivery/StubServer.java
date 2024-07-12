package dev.hookdock.delivery;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import com.sun.net.httpserver.HttpServer;

/**
 * Tiny HTTP target for delivery tests. Responds based on the request path:
 * /ok, /fail (500), /redirect (302), /slow (sleeps past the client timeout).
 */
class StubServer implements AutoCloseable {

    record Received(String method, String path, String query, Map<String, List<String>> headers, byte[] body) {
    }

    private final HttpServer server;
    final List<Received> received = new CopyOnWriteArrayList<>();

    StubServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] body = exchange.getRequestBody().readAllBytes();
            String path = exchange.getRequestURI().getPath();
            received.add(new Received(exchange.getRequestMethod(), path, exchange.getRequestURI().getRawQuery(),
                    Map.copyOf(exchange.getRequestHeaders()), body));
            int status = switch (path) {
                case "/fail" -> 500;
                case "/redirect" -> {
                    exchange.getResponseHeaders().add("Location", "http://127.0.0.1:1/elsewhere");
                    yield 302;
                }
                case "/slow" -> {
                    sleep(2000);
                    yield 200;
                }
                default -> 200;
            };
            byte[] response = ("{\"handled\":\"" + path + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
    }

    String url(String path) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + path;
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
