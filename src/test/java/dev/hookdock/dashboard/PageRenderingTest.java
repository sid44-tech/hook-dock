package dev.hookdock.dashboard;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import dev.hookdock.TestData;
import dev.hookdock.destinations.DestinationService;
import dev.hookdock.endpoints.CreatedEndpoint;
import dev.hookdock.endpoints.EndpointService;
import dev.hookdock.projects.Project;
import dev.hookdock.security.AppUser;

/**
 * Smoke tests that the main pages render with realistic data, so template
 * mistakes show up in CI rather than in the browser.
 */
@SpringBootTest
@AutoConfigureMockMvc
class PageRenderingTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestData testData;

    @Autowired
    DestinationService destinationService;

    @Autowired
    EndpointService endpointService;

    @Autowired
    ObjectMapper objectMapper;

    @Test
    void mainPagesRender() throws Exception {
        AppUser owner = testData.user("renderer");
        Project project = testData.project(owner, "render-project");
        CreatedEndpoint endpoint = testData.endpoint(project, "render-endpoint");
        // Port 1 on loopback refuses connections, which gives us a failed attempt to show.
        var destination = destinationService.create(project.getId(), "render-dest", "http://127.0.0.1:1/hook");
        endpointService.configureForwarding(endpoint.endpoint().getId(), destination.getId());

        String response = mvc.perform(post("/in/" + endpoint.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-GitHub-Event", "push")
                        .content("{\"hello\":\"<world>\"}"))
                .andReturn().getResponse().getContentAsString();
        UUID eventId = UUID.fromString(objectMapper.readTree(response).get("id").asText());

        mvc.perform(get("/").with(user("renderer")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("render-endpoint")));
        mvc.perform(get("/projects").with(user("renderer")))
                .andExpect(status().isOk());
        mvc.perform(get("/projects/" + project.getId()).with(user("renderer")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("render-dest")));
        mvc.perform(get("/projects/" + project.getId() + "/events").with(user("renderer")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("pending")));
        mvc.perform(get("/events/" + eventId).with(user("renderer")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("AUTO_FORWARD")))
                // Payload must be escaped, never rendered as markup.
                .andExpect(content().string(containsString("&lt;world&gt;")));
    }

    @Test
    void loginPageIsPublic() throws Exception {
        mvc.perform(get("/login")).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
