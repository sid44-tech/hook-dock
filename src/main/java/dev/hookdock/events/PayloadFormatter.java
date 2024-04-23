package dev.hookdock.events;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.stereotype.Component;

/**
 * Renders a stored body for display. Never mutates the stored bytes.
 */
@Component
public class PayloadFormatter {

    public record FormattedPayload(String raw, String pretty, boolean binary) {
    }

    private final ObjectMapper objectMapper;

    public PayloadFormatter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public FormattedPayload format(byte[] body, String contentType) {
        if (body == null || body.length == 0) {
            return new FormattedPayload("", null, false);
        }
        String text = decodeUtf8(body);
        if (text == null) {
            return new FormattedPayload(null, null, true);
        }
        return new FormattedPayload(text, looksLikeJson(contentType, text) ? prettyJson(text) : null, false);
    }

    private static boolean looksLikeJson(String contentType, String text) {
        if (contentType != null && contentType.toLowerCase(Locale.ROOT).contains("json")) {
            return true;
        }
        String trimmed = text.stripLeading();
        return trimmed.startsWith("{") || trimmed.startsWith("[");
    }

    private String prettyJson(String text) {
        try {
            JsonNode node = objectMapper.readTree(text);
            return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            return null;
        }
    }

    private static String decodeUtf8(byte[] body) {
        try {
            return StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(body))
                    .toString();
        } catch (CharacterCodingException e) {
            return null;
        }
    }
}
