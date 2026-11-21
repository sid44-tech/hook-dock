package dev.hookdock.ingestion;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import dev.hookdock.ratelimit.RateLimitStatus;

@RestController
public class IngestionController {

    static final String RATE_LIMIT_LIMIT = "RateLimit-Limit";
    static final String RATE_LIMIT_REMAINING = "RateLimit-Remaining";
    static final String RATE_LIMIT_RESET = "RateLimit-Reset";

    private final CaptureService captureService;
    private final long maxBodyBytes;

    public IngestionController(CaptureService captureService, IngestionProperties properties) {
        this.captureService = captureService;
        this.maxBodyBytes = properties.maxBodySize().toBytes();
    }

    /*
     * Read the body straight off the input stream. Binding it as a String
     * decoded/re-encoded it, and for form posts Spring rebuilds the body from
     * the parsed parameters, which breaks signature verification downstream.
     */
    @RequestMapping(value = "/in/{token}", method = { RequestMethod.POST, RequestMethod.PUT, RequestMethod.PATCH })
    public ResponseEntity<Map<String, Object>> receive(@PathVariable String token, HttpServletRequest request)
            throws IOException {
        InboundRequest inbound = new InboundRequest(
                request.getMethod(),
                "/in/" + mask(token),
                request.getQueryString(),
                headers(request),
                readBody(request),
                request.getContentType());

        return switch (captureService.capture(token, inbound)) {
            case CaptureResult.Captured captured -> {
                ResponseEntity.BodyBuilder response = ResponseEntity.ok();
                captured.rateLimit().ifPresent(status -> rateLimitHeaders(response, status));
                yield response.body(Map.of("status", "captured", "id", captured.event().getId()));
            }
            case CaptureResult.NotFound notFound -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(Map.of("status", "not_found"));
            case CaptureResult.RateLimited limited -> rateLimitHeaders(
                    ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS), limited.rateLimit())
                    .header(HttpHeaders.RETRY_AFTER, String.valueOf(seconds(limited.rateLimit().resetAfter())))
                    .body(Map.of("status", "rate_limited"));
        };
    }

    /*
     * RateLimit-Limit / -Remaining / -Reset as in the IETF httpapi rate limit
     * draft (the widely deployed three-header form). Reset is delta seconds,
     * not a timestamp: the time until the oldest counted request leaves the
     * rolling window, which on a 429 is the same as Retry-After.
     */
    private static ResponseEntity.BodyBuilder rateLimitHeaders(ResponseEntity.BodyBuilder response,
            RateLimitStatus status) {
        return response
                .header(RATE_LIMIT_LIMIT, String.valueOf(status.limit()))
                .header(RATE_LIMIT_REMAINING, String.valueOf(status.remaining()))
                .header(RATE_LIMIT_RESET, String.valueOf(seconds(status.resetAfter())));
    }

    // Whole seconds, rounded up so a client that waits this long is never early,
    // and at least 1 so a client never retries immediately.
    private static long seconds(Duration duration) {
        long nanos = duration.toNanos();
        long seconds = nanos / 1_000_000_000L + (nanos % 1_000_000_000L == 0 ? 0 : 1);
        return Math.max(1, seconds);
    }

    @ExceptionHandler(PayloadTooLargeException.class)
    public ResponseEntity<Map<String, Object>> tooLarge(PayloadTooLargeException e) {
        return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(Map.of("status", "payload_too_large"));
    }

    /*
     * Check Content-Length first so we can reject early, but don't trust it:
     * chunked requests don't send one, so cap the actual read as well.
     */
    private byte[] readBody(HttpServletRequest request) throws IOException {
        if (request.getContentLengthLong() > maxBodyBytes) {
            throw new PayloadTooLargeException(maxBodyBytes);
        }
        try (InputStream in = request.getInputStream()) {
            byte[] body = in.readNBytes((int) maxBodyBytes + 1);
            if (body.length > maxBodyBytes) {
                throw new PayloadTooLargeException(maxBodyBytes);
            }
            return body;
        }
    }

    // The token is a secret - never persist it as part of the stored path.
    private static String mask(String token) {
        return token.length() <= 4 ? "****" : token.substring(0, 4) + "****";
    }

    private static Map<String, List<String>> headers(HttpServletRequest request) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (String name : Collections.list(request.getHeaderNames())) {
            headers.put(name.toLowerCase(Locale.ROOT), new ArrayList<>(Collections.list(request.getHeaders(name))));
        }
        return headers;
    }
}
