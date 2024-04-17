package dev.hookdock.ingestion;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class IngestionController {

    private final CaptureService captureService;

    public IngestionController(CaptureService captureService) {
        this.captureService = captureService;
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
                request.getInputStream().readAllBytes(),
                request.getContentType());

        return captureService.capture(token, inbound)
                .<ResponseEntity<Map<String, Object>>>map(event -> ResponseEntity.ok(
                        Map.of("status", "captured", "id", event.getId())))
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("status", "not_found")));
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
