package dev.hookdock.endpoints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import dev.hookdock.TestData;
import dev.hookdock.projects.Project;

/**
 * The per-endpoint rate limit field on the project page: saving, clearing,
 * validation and access checks.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EndpointRateLimitTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestData testData;

    @Autowired
    EndpointService endpointService;

    Project project;
    UUID endpointId;
    UUID otherEndpointId;

    @BeforeEach
    void setUp() {
        project = testData.project(testData.user("limit-owner"), "limits");
        testData.user("limit-intruder");
        endpointId = testData.endpoint(project, "limited").endpoint().getId();
        otherEndpointId = testData.endpoint(project, "untouched").endpoint().getId();
    }

    @Test
    void ownerCanSetAnOverrideForOneEndpoint() throws Exception {
        save(endpointId, "250")
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/" + project.getId()))
                .andExpect(flash().attributeCount(0));

        assertThat(endpointService.get(endpointId).getRateLimitRequests()).isEqualTo(250);
        assertThat(endpointService.get(otherEndpointId).getRateLimitRequests()).isNull();
    }

    @Test
    void surroundingWhitespaceIsIgnored() throws Exception {
        save(endpointId, " 42 ").andExpect(flash().attributeCount(0));
        assertThat(endpointService.get(endpointId).getRateLimitRequests()).isEqualTo(42);
    }

    @Test
    void blankClearsTheOverride() throws Exception {
        endpointService.setRateLimit(endpointId, 250);

        save(endpointId, "").andExpect(status().is3xxRedirection()).andExpect(flash().attributeCount(0));

        assertThat(endpointService.get(endpointId).getRateLimitRequests()).isNull();
    }

    @Test
    void missingParameterClearsTheOverride() throws Exception {
        endpointService.setRateLimit(endpointId, 250);

        mvc.perform(post("/endpoints/" + endpointId + "/rate-limit").with(user("limit-owner")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        assertThat(endpointService.get(endpointId).getRateLimitRequests()).isNull();
    }

    @Test
    void savingTheServerDefaultClearsTheOverride() throws Exception {
        endpointService.setRateLimit(endpointId, 250);

        // The test config's default is 5.
        save(endpointId, "5").andExpect(status().is3xxRedirection()).andExpect(flash().attributeCount(0));

        assertThat(endpointService.get(endpointId).getRateLimitRequests()).isNull();
    }

    @Test
    void projectPageShowsThePlaceholderAfterSavingTheDefault() throws Exception {
        save(endpointId, "250");
        save(endpointId, "5");

        mvc.perform(get("/projects/" + project.getId()).with(user("limit-owner")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("value=\"250\""))))
                .andExpect(content().string(not(containsString("value=\"5\""))))
                .andExpect(content().string(containsString("placeholder=\"5 (default)\"")));
    }

    @Test
    void valuesNextToTheDefaultAreStillOverrides() throws Exception {
        save(endpointId, "4");
        assertThat(endpointService.get(endpointId).getRateLimitRequests()).isEqualTo(4);
        save(endpointId, "6");
        assertThat(endpointService.get(endpointId).getRateLimitRequests()).isEqualTo(6);
    }

    @ParameterizedTest
    @ValueSource(strings = { "0", "-5", "10001", "abc", "1.5", "1e3", "99999999999" })
    void invalidValuesAreRejectedAndLeaveTheLimitUnchanged(String value) throws Exception {
        endpointService.setRateLimit(endpointId, 250);

        save(endpointId, value)
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/projects/" + project.getId()))
                .andExpect(flash().attribute("error", containsString("from 1 to 10000")));

        assertThat(endpointService.get(endpointId).getRateLimitRequests()).isEqualTo(250);
    }

    @Test
    void upperBoundIsAccepted() throws Exception {
        save(endpointId, "10000").andExpect(flash().attributeCount(0));
        assertThat(endpointService.get(endpointId).getRateLimitRequests()).isEqualTo(10000);
    }

    @Test
    void otherUsersGet404AndCannotChangeTheLimit() throws Exception {
        mvc.perform(post("/endpoints/" + endpointId + "/rate-limit").param("rateLimit", "1")
                        .with(user("limit-intruder")).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(endpointService.get(endpointId).getRateLimitRequests()).isNull();
    }

    @Test
    void unknownEndpointIs404() throws Exception {
        save(UUID.randomUUID(), "10").andExpect(status().isNotFound());
    }

    @Test
    void requiresCsrf() throws Exception {
        mvc.perform(post("/endpoints/" + endpointId + "/rate-limit").param("rateLimit", "10")
                        .with(user("limit-owner")))
                .andExpect(status().isForbidden());

        assertThat(endpointService.get(endpointId).getRateLimitRequests()).isNull();
    }

    @Test
    void projectPageShowsTheOverrideOrTheDefault() throws Exception {
        endpointService.setRateLimit(endpointId, 250);

        mvc.perform(get("/projects/" + project.getId()).with(user("limit-owner")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("<th>Rate limit</th>")))
                .andExpect(content().string(containsString(
                        "action=\"/endpoints/" + endpointId + "/rate-limit\"")))
                .andExpect(content().string(containsString(
                        "action=\"/endpoints/" + otherEndpointId + "/rate-limit\"")))
                .andExpect(content().string(containsString("value=\"250\"")))
                // Default from the test config is 5 requests per 1m window.
                .andExpect(content().string(containsString("placeholder=\"5 (default)\"")))
                .andExpect(content().string(containsString("/ min")))
                .andExpect(content().string(not(containsString("Rate limiting is turned off"))));
    }

    private ResultActions save(UUID id, String value) throws Exception {
        return mvc.perform(post("/endpoints/" + id + "/rate-limit").param("rateLimit", value)
                .with(user("limit-owner")).with(csrf()));
    }
}
