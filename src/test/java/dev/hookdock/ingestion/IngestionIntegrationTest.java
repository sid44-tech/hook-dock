package dev.hookdock.ingestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import dev.hookdock.TestData;
import dev.hookdock.delivery.DeliveryAttemptRepository;
import dev.hookdock.destinations.DestinationService;
import dev.hookdock.endpoints.CreatedEndpoint;
import dev.hookdock.endpoints.EndpointService;
import dev.hookdock.events.CapturedWebhook;
import dev.hookdock.events.CapturedWebhookRepository;
import dev.hookdock.projects.Project;

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

    @Autowired
    DeliveryAttemptRepository attempts;

    @Autowired
    DestinationService destinationService;

    @Value("${hookdock.rate-limit.requests}")
    int rateLimit;

    CreatedEndpoint endpoint;
    Project project;

    @BeforeEach
    void setUp() {
        var owner = testData.user("ingest-owner");
        project = testData.project(owner, "ingest");
        endpoint = testData.endpoint(project, "github");
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

    @Test
    void overTheLimitIs429WithRetryAfterAndStoresNothing() throws Exception {
        // Forwarding is on so we can check a rejected request doesn't queue a delivery either.
        var destination = destinationService.create(project.getId(), "target", "http://127.0.0.1:1/hook");
        endpointService.configureForwarding(endpoint.endpoint().getId(), destination.getId());

        for (int i = 0; i < rateLimit; i++) {
            mvc.perform(post("/in/" + endpoint.token()).content("{\"n\":" + i + "}"))
                    .andExpect(status().isOk());
        }
        long eventsBefore = events.count();
        long attemptsBefore = attempts.count();

        mvc.perform(post("/in/" + endpoint.token()).content("{\"n\":\"over\"}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", matchesPattern("[1-9][0-9]*")))
                .andExpect(jsonPath("$.status").value("rate_limited"));

        assertThat(events.count()).isEqualTo(eventsBefore);
        assertThat(attempts.count()).isEqualTo(attemptsBefore);
    }

    @Test
    void limitIsPerEndpoint() throws Exception {
        CreatedEndpoint other = testData.endpoint(project, "other");

        for (int i = 0; i < rateLimit; i++) {
            mvc.perform(post("/in/" + endpoint.token()).content("{}")).andExpect(status().isOk());
        }
        mvc.perform(post("/in/" + endpoint.token()).content("{}")).andExpect(status().isTooManyRequests());

        mvc.perform(post("/in/" + other.token()).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("captured"));
    }

    @Test
    void unknownAndDisabledEndpointsStay404WhenFlooded() throws Exception {
        // Limiter state is keyed by real endpoints, so junk tokens never get a 429.
        for (int i = 0; i < rateLimit + 5; i++) {
            mvc.perform(post("/in/not-a-real-token").content("{}")).andExpect(status().isNotFound());
        }

        endpointService.setEnabled(endpoint.endpoint().getId(), false);
        for (int i = 0; i < rateLimit + 5; i++) {
            mvc.perform(post("/in/" + endpoint.token()).content("{}")).andExpect(status().isNotFound());
        }
    }

    @Test
    void oversizedBodyStill413WhenOverTheLimit() throws Exception {
        // The size check happens before the limiter, same as before rate limiting existed.
        for (int i = 0; i < rateLimit; i++) {
            mvc.perform(post("/in/" + endpoint.token()).content("{}")).andExpect(status().isOk());
        }
        mvc.perform(post("/in/" + endpoint.token()).content(new byte[2048]))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    void successfulCapturesCarryRateLimitHeadersCountingDown() throws Exception {
        for (int i = 1; i <= rateLimit; i++) {
            mvc.perform(post("/in/" + endpoint.token()).content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("RateLimit-Limit", String.valueOf(rateLimit)))
                    .andExpect(header().string("RateLimit-Remaining", String.valueOf(rateLimit - i)))
                    // Window is 1m in the test config; the oldest request leaves within it.
                    .andExpect(header().string("RateLimit-Reset", matchesPattern("[1-9][0-9]?")))
                    .andExpect(header().doesNotExist("Retry-After"));
        }
    }

    @Test
    void rateLimitedResponseCarriesRateLimitHeadersMatchingRetryAfter() throws Exception {
        for (int i = 0; i < rateLimit; i++) {
            mvc.perform(post("/in/" + endpoint.token()).content("{}")).andExpect(status().isOk());
        }
        var response = mvc.perform(post("/in/" + endpoint.token()).content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("RateLimit-Limit", String.valueOf(rateLimit)))
                .andExpect(header().string("RateLimit-Remaining", "0"))
                .andReturn().getResponse();
        assertThat(response.getHeader("RateLimit-Reset")).isEqualTo(response.getHeader("Retry-After"));
    }

    @Test
    void noRateLimitHeadersOn404Or413() throws Exception {
        // No endpoint was resolved (404), or the body was rejected before the
        // endpoint was looked up (413), so there is no quota to report.
        mvc.perform(post("/in/not-a-real-token").content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(header().doesNotExist("RateLimit-Limit"))
                .andExpect(header().doesNotExist("RateLimit-Remaining"))
                .andExpect(header().doesNotExist("RateLimit-Reset"));
        mvc.perform(post("/in/" + endpoint.token()).content(new byte[2048]))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(header().doesNotExist("RateLimit-Limit"))
                .andExpect(header().doesNotExist("RateLimit-Remaining"))
                .andExpect(header().doesNotExist("RateLimit-Reset"));
    }

    @Test
    void endpointOverrideAppliesToThatEndpointOnly() throws Exception {
        CreatedEndpoint other = testData.endpoint(project, "other");
        int override = rateLimit + 3;
        endpointService.setRateLimit(endpoint.endpoint().getId(), override);

        for (int i = 1; i <= override; i++) {
            mvc.perform(post("/in/" + endpoint.token()).content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("RateLimit-Limit", String.valueOf(override)))
                    .andExpect(header().string("RateLimit-Remaining", String.valueOf(override - i)));
        }
        mvc.perform(post("/in/" + endpoint.token()).content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("RateLimit-Limit", String.valueOf(override)));

        // The other endpoint in the same project still has the server default.
        mvc.perform(post("/in/" + other.token()).content("{}"))
                .andExpect(status().isOk())
                .andExpect(header().string("RateLimit-Limit", String.valueOf(rateLimit)))
                .andExpect(header().string("RateLimit-Remaining", String.valueOf(rateLimit - 1)));
    }

    @Test
    void changingTheOverrideTakesEffectOnTheNextRequest() throws Exception {
        for (int i = 0; i < rateLimit; i++) {
            mvc.perform(post("/in/" + endpoint.token()).content("{}")).andExpect(status().isOk());
        }
        mvc.perform(post("/in/" + endpoint.token()).content("{}")).andExpect(status().isTooManyRequests());

        // Raising the limit frees up exactly the extra requests in the current window.
        endpointService.setRateLimit(endpoint.endpoint().getId(), rateLimit + 1);
        mvc.perform(post("/in/" + endpoint.token()).content("{}"))
                .andExpect(status().isOk())
                .andExpect(header().string("RateLimit-Limit", String.valueOf(rateLimit + 1)))
                .andExpect(header().string("RateLimit-Remaining", "0"));
        mvc.perform(post("/in/" + endpoint.token()).content("{}")).andExpect(status().isTooManyRequests());

        // Clearing the override goes back to the default, still counting the recent requests.
        endpointService.setRateLimit(endpoint.endpoint().getId(), null);
        mvc.perform(post("/in/" + endpoint.token()).content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("RateLimit-Limit", String.valueOf(rateLimit)));
    }

    private UUID idFrom(String response) throws Exception {
        JsonNode json = objectMapper.readTree(response);
        return UUID.fromString(json.get("id").asText());
    }
}
