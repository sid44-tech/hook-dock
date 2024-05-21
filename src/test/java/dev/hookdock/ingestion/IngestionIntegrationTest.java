package dev.hookdock.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import dev.hookdock.TestData;
import dev.hookdock.endpoints.CreatedEndpoint;
import dev.hookdock.endpoints.EndpointService;
import dev.hookdock.events.CapturedWebhook;
import dev.hookdock.events.CapturedWebhookRepository;

@SpringBootTest
@AutoConfigureMockMvc
class IngestionIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestData testData;

    @Autowired
    EndpointService endpointService;

    @Autowired
    CapturedWebhookRepository events;

    @Autowired
    ObjectMapper objectMapper;

    CreatedEndpoint endpoint;

    @BeforeEach
    void setUp() {
        var owner = testData.user("ingest-owner");
        endpoint = testData.endpoint(testData.project(owner, "ingest"), "github");
    }

    @Test
    void capturesJsonWebhookWithoutCsrfOrLogin() throws Exception {
        String body = "{\"action\":\"opened\"}";
        String response = mvc.perform(post("/in/" + endpoint.token() + "?source=test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "pull_request")
                        .header("X-GitHub-Delivery", "abc-123")
                        .header("Authorization", "Bearer top-secret")
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("captured"))
                .andReturn().getResponse().getContentAsString();

        CapturedWebhook saved = events.findById(idFrom(response)).orElseThrow();
        assertThat(new String(saved.getRequestBody(), StandardCharsets.UTF_8)).isEqualTo(body);
        assertThat(saved.getQueryString()).isEqualTo("source=test");
        assertThat(saved.getProvider()).isEqualTo("github");
        assertThat(saved.getProviderEventId()).isEqualTo("abc-123");
        assertThat(saved.getRequestHeaders().get("authorization")).containsExactly("[REDACTED]");
        assertThat(saved.getRawPath()).doesNotContain(endpoint.token());
    }

    @Test
    void keepsFormBodiesByteForByte() throws Exception {
        // Parameter order and encoding must survive, or signatures won't verify.
        String body = "b=2&a=hello%20world&a=again";
        String response = mvc.perform(post("/in/" + endpoint.token())
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        CapturedWebhook saved = events.findById(idFrom(response)).orElseThrow();
        assertThat(new String(saved.getRequestBody(), StandardCharsets.UTF_8)).isEqualTo(body);
    }

    @Test
    void unknownTokenIs404() throws Exception {
        mvc.perform(post("/in/not-a-real-token").content("{}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void disabledEndpointIs404() throws Exception {
        endpointService.setEnabled(endpoint.endpoint().getId(), false);
        long before = events.count();

        mvc.perform(post("/in/" + endpoint.token()).content("{}"))
                .andExpect(status().isNotFound());

        assertThat(events.count()).isEqualTo(before);
    }

    @Test
    void oversizedBodyIs413() throws Exception {
        mvc.perform(post("/in/" + endpoint.token()).content(new byte[2048]))
                .andExpect(status().isPayloadTooLarge());
    }

    private UUID idFrom(String response) throws Exception {
        JsonNode json = objectMapper.readTree(response);
        return UUID.fromString(json.get("id").asText());
    }
}
