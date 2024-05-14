package dev.hookdock.ingestion;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IngestionController {

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

        return captureService.capture(token, inbound)
                .<ResponseEntity<Map<String, Object>>>map(event -> ResponseEntity.ok(
                        Map.of("status", "captured", "id", event.getId())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "not_found")));
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
