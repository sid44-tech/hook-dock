package dev.hookdock.ingestion;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import dev.hookdock.TestData;
import dev.hookdock.endpoints.CreatedEndpoint;
import dev.hookdock.endpoints.EndpointService;
import dev.hookdock.projects.Project;

@SpringBootTest(properties = "hookdock.rate-limit.enabled=false")
@AutoConfigureMockMvc
class RateLimitDisabledIntegrationTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestData testData;

    @Autowired
    EndpointService endpointService;

    @Test
    void everyRequestIsCapturedAndNoRateLimitHeadersAreSent() throws Exception {
        CreatedEndpoint endpoint = testData.endpoint(
                testData.project(testData.user("unlimited-owner"), "unlimited"), "unlimited");

        // Well past the limit of 5 in the test config.
        for (int i = 0; i < 15; i++) {
            mvc.perform(post("/in/" + endpoint.token()).content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist("RateLimit-Limit"))
                    .andExpect(header().doesNotExist("RateLimit-Remaining"))
                    .andExpect(header().doesNotExist("RateLimit-Reset"));
        }
    }

    @Test
    void endpointOverridesAreNotEnforcedEither() throws Exception {
        CreatedEndpoint endpoint = testData.endpoint(
                testData.project(testData.user("unlimited-owner"), "unlimited-override"), "override");
        endpointService.setRateLimit(endpoint.endpoint().getId(), 1);

        for (int i = 0; i < 3; i++) {
            mvc.perform(post("/in/" + endpoint.token()).content("{}"))
                    .andExpect(status().isOk())
                    .andExpect(header().doesNotExist("RateLimit-Limit"));
        }
    }

    @Test
    void projectPageSaysLimitsAreNotEnforced() throws Exception {
        Project project = testData.project(testData.user("unlimited-viewer"), "unlimited-page");
        testData.endpoint(project, "shown");

        mvc.perform(get("/projects/" + project.getId()).with(user("unlimited-viewer")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Rate limiting is turned off on this server")));
    }
}
