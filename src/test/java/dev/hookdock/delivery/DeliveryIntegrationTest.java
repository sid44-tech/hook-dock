package dev.hookdock.delivery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import dev.hookdock.TestData;
import dev.hookdock.destinations.Destination;
import dev.hookdock.destinations.DestinationRepository;
import dev.hookdock.destinations.DestinationService;
import dev.hookdock.endpoints.CreatedEndpoint;
import dev.hookdock.endpoints.EndpointService;
import dev.hookdock.events.CapturedWebhook;
import dev.hookdock.events.CapturedWebhookRepository;
import dev.hookdock.projects.Project;
import dev.hookdock.security.AppUser;

@SpringBootTest
@AutoConfigureMockMvc
class DeliveryIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestData testData;

    @Autowired
    EndpointService endpointService;

    @Autowired
    DestinationService destinationService;

    @Autowired
    DestinationRepository destinationRepository;

    @Autowired
    DeliveryAttemptRepository attempts;

    @Autowired
    CapturedWebhookRepository events;

    @Autowired
    DeliveryWorker worker;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    TransactionTemplate tx;

    StubServer stub;
    AppUser owner;
    Project project;
    CreatedEndpoint endpoint;

    @BeforeEach
    void setUp() throws Exception {
        stub = new StubServer();
        owner = testData.user("delivery-owner");
        project = testData.project(owner, "delivery");
        endpoint = testData.endpoint(project, "forwarded");
    }

    @AfterEach
    void tearDown() {
        stub.close();
    }

    @Test
    void forwardsTheOriginalRequest() throws Exception {
        forwardTo("/ok");
        UUID eventId = capture("{\"id\":\"evt_1\"}");

        worker.poll();

        DeliveryAttempt attempt = onlyAttempt(eventId);
        assertThat(attempt.getStatus()).isEqualTo(DeliveryStatus.SUCCEEDED);
        assertThat(attempt.getTriggerType()).isEqualTo(TriggerType.AUTO_FORWARD);
        assertThat(attempt.getHttpStatus()).isEqualTo(200);
        assertThat(attempt.getResponseBodyExcerpt()).contains("/ok");
        assertThat(attempt.getDurationMs()).isNotNull();

        StubServer.Received received = stub.received.get(0);
        assertThat(new String(received.body(), StandardCharsets.UTF_8)).isEqualTo("{\"id\":\"evt_1\"}");
        assertThat(received.query()).isEqualTo("a=1");
        assertThat(received.headers()).containsKey("X-github-event");
        assertThat(received.headers().get("X-hookdock-event-id")).containsExactly(eventId.toString());
        assertThat(received.headers()).doesNotContainKey("Authorization");
    }

    @Test
    void non2xxIsAFailedDelivery() throws Exception {
        forwardTo("/fail");
        UUID eventId = capture("{}");

        worker.poll();

        DeliveryAttempt attempt = onlyAttempt(eventId);
        assertThat(attempt.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(attempt.getHttpStatus()).isEqualTo(500);
        assertThat(attempt.getErrorCategory()).isEqualTo(ErrorCategory.HTTP_STATUS);
    }

    @Test
    void redirectsAreNotFollowed() throws Exception {
        forwardTo("/redirect");
        UUID eventId = capture("{}");

        worker.poll();

        DeliveryAttempt attempt = onlyAttempt(eventId);
        assertThat(attempt.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(attempt.getHttpStatus()).isEqualTo(302);
        assertThat(stub.received).hasSize(1);
    }

    @Test
    void slowTargetsTimeOut() throws Exception {
        forwardTo("/slow");
        UUID eventId = capture("{}");

        worker.poll();

        DeliveryAttempt attempt = onlyAttempt(eventId);
        assertThat(attempt.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(attempt.getErrorCategory()).isEqualTo(ErrorCategory.TIMEOUT);
    }

    @Test
    void connectionErrorsAreRecorded() throws Exception {
        String url = stub.url("/ok");
        stub.close();
        forwardToUrl(url);
        UUID eventId = capture("{}");

        worker.poll();

        DeliveryAttempt attempt = onlyAttempt(eventId);
        assertThat(attempt.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(attempt.getErrorCategory()).isEqualTo(ErrorCategory.CONNECTION);
    }

    @Test
    void disabledDestinationIsBlockedAtSendTime() throws Exception {
        Destination destination = forwardTo("/ok");
        UUID eventId = capture("{}");
        destinationService.setEnabled(destination.getId(), false);

        worker.poll();

        DeliveryAttempt attempt = onlyAttempt(eventId);
        assertThat(attempt.getStatus()).isEqualTo(DeliveryStatus.BLOCKED);
        assertThat(stub.received).isEmpty();
    }

    @Test
    void anAttemptCanOnlyBeClaimedOnce() throws Exception {
        forwardTo("/ok");
        UUID attemptId = onlyAttempt(capture("{}")).getId();
        Instant now = Instant.now();

        int first = claim(attemptId, "a", now);
        int second = claim(attemptId, "b", now);

        assertThat(first).isEqualTo(1);
        assertThat(second).isZero();
    }

    @Test
    void ownerCanReplayAndOriginalIsUntouched() throws Exception {
        Destination destination = destinationService.create(project.getId(), "target", stub.url("/ok"));
        UUID eventId = capture("{\"n\":1}");
        CapturedWebhook before = events.findById(eventId).orElseThrow();

        mvc.perform(post("/events/" + eventId + "/replays")
                        .param("destinationId", destination.getId().toString())
                        .param("confirm", "true")
                        .with(user(owner.getUsername())).with(csrf()))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/events/" + eventId + "/replays")
                        .param("destinationId", destination.getId().toString())
                        .param("confirm", "true")
                        .with(user(owner.getUsername())).with(csrf()))
                .andExpect(status().is3xxRedirection());
        worker.poll();

        List<DeliveryAttempt> all = attempts.findByCapturedWebhookIdOrderByAttemptNumberAsc(eventId);
        assertThat(all).extracting(DeliveryAttempt::getAttemptNumber).containsExactly(1, 2);
        assertThat(all).allSatisfy(a -> {
            assertThat(a.getTriggerType()).isEqualTo(TriggerType.MANUAL_REPLAY);
            assertThat(a.getRequestedByUserId()).isEqualTo(owner.getId());
            assertThat(a.getStatus()).isEqualTo(DeliveryStatus.SUCCEEDED);
        });
        CapturedWebhook after = events.findById(eventId).orElseThrow();
        assertThat(after.getRequestBody()).isEqualTo(before.getRequestBody());
        assertThat(after.getReceivedAt()).isEqualTo(before.getReceivedAt());
    }

    @Test
    void replayNeedsConfirmation() throws Exception {
        Destination destination = destinationService.create(project.getId(), "target", stub.url("/ok"));
        UUID eventId = capture("{}");

        mvc.perform(post("/events/" + eventId + "/replays")
                        .param("destinationId", destination.getId().toString())
                        .with(user(owner.getUsername())).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(attempts.findByCapturedWebhookIdOrderByAttemptNumberAsc(eventId)).isEmpty();
    }

    @Test
    void otherUsersCannotReplay() throws Exception {
        Destination destination = destinationService.create(project.getId(), "target", stub.url("/ok"));
        UUID eventId = capture("{}");
        testData.user("mallory");

        mvc.perform(post("/events/" + eventId + "/replays")
                        .param("destinationId", destination.getId().toString())
                        .param("confirm", "true")
                        .with(user("mallory")).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(attempts.findByCapturedWebhookIdOrderByAttemptNumberAsc(eventId)).isEmpty();
    }

    @Test
    void cannotReplayToAnotherProjectsDestination() throws Exception {
        Project other = testData.project(owner, "other");
        Destination foreign = destinationService.create(other.getId(), "foreign", stub.url("/ok"));
        UUID eventId = capture("{}");

        mvc.perform(post("/events/" + eventId + "/replays")
                        .param("destinationId", foreign.getId().toString())
                        .param("confirm", "true")
                        .with(user(owner.getUsername())).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(attempts.findByCapturedWebhookIdOrderByAttemptNumberAsc(eventId)).isEmpty();
    }

    private Destination forwardTo(String path) {
        return forwardToUrl(stub.url(path));
    }

    private Destination forwardToUrl(String url) {
        Destination destination = destinationService.create(project.getId(), "target", url);
        endpointService.configureForwarding(endpoint.endpoint().getId(), destination.getId());
        return destination;
    }

    private UUID capture(String body) throws Exception {
        String response = mvc.perform(post("/in/" + endpoint.token() + "?a=1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "push")
                        .header("Authorization", "Bearer secret")
                        .content(body))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(objectMapper.readTree(response).get("id").asText());
    }

    private DeliveryAttempt onlyAttempt(UUID eventId) {
        List<DeliveryAttempt> list = attempts.findByCapturedWebhookIdOrderByAttemptNumberAsc(eventId);
        assertThat(list).hasSize(1);
        return list.get(0);
    }

    private int claim(UUID id, String owner, Instant now) {
        return tx.execute(s -> attempts.claim(id, owner, now, now.plusSeconds(60)));
    }
}
