package dev.hookdock.events;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;

class PayloadFormatterTest {

    private final PayloadFormatter formatter = new PayloadFormatter(new ObjectMapper());

    @Test
    void prettyPrintsJson() {
        var result = formatter.format("{\"a\":1}".getBytes(StandardCharsets.UTF_8), "application/json");
        assertThat(result.raw()).isEqualTo("{\"a\":1}");
        assertThat(result.pretty()).contains("\"a\" : 1");
    }

    @Test
    void leavesInvalidJsonAsRawOnly() {
        var result = formatter.format("{not json".getBytes(StandardCharsets.UTF_8), "application/json");
        assertThat(result.pretty()).isNull();
        assertThat(result.raw()).isEqualTo("{not json");
    }

    @Test
    void flagsBinaryBodies() {
        var result = formatter.format(new byte[] { (byte) 0xff, (byte) 0xfe, 0x00 }, "application/octet-stream");
        assertThat(result.binary()).isTrue();
    }
}
