package dev.hookdock.endpoints;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import dev.hookdock.TestData;
import dev.hookdock.projects.Project;

/**
 * "Same as the default" is decided by the server's configured default, not a
 * fixed number. 250 is deliberately above 127 so that comparing Integer
 * objects by reference instead of by value would fail here.
 */
@SpringBootTest(properties = "hookdock.rate-limit.requests=250")
@AutoConfigureMockMvc
class EndpointRateLimitCustomDefaultTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestData testData;

    @Autowired
    EndpointService endpointService;

    Project project;
    UUID endpointId;

    @BeforeEach
    void setUp() {
        project = testData.project(testData.user("custom-default-owner"), "custom-default");
        endpointId = testData.endpoint(project, "custom").endpoint().getId();
    }

    @Test
    void savingTheConfiguredDefaultClearsTheOverride() throws Exception {
        endpointService.setRateLimit(endpointId, 40);

        save("250");

        assertThat(endpointService.get(endpointId).getRateLimitRequests()).isNull();
        mvc.perform(get("/projects/" + project.getId()).with(user("custom-default-owner")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("placeholder=\"250 (default)\"")))
                .andExpect(content().string(not(containsString("value=\"250\""))))
                .andExpect(content().string(not(containsString("value=\"40\""))));
    }

    @Test
    void theStockDefaultOf20IsAnOverrideWhenTheServerDefaultDiffers() throws Exception {
        save("20");

        assertThat(endpointService.get(endpointId).getRateLimitRequests()).isEqualTo(20);
        mvc.perform(get("/projects/" + project.getId()).with(user("custom-default-owner")))
                .andExpect(content().string(containsString("value=\"20\"")));
    }

    private void save(String value) throws Exception {
        mvc.perform(post("/endpoints/" + endpointId + "/rate-limit").param("rateLimit", value)
                        .with(user("custom-default-owner")).with(csrf()))
                .andExpect(status().is3xxRedirection());
    }
}
