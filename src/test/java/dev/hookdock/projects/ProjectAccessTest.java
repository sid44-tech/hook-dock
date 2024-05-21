package dev.hookdock.projects;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrlPattern;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import dev.hookdock.TestData;

@SpringBootTest
@AutoConfigureMockMvc
class ProjectAccessTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestData testData;

    Project aliceProject;

    @BeforeEach
    void setUp() {
        aliceProject = testData.project(testData.user("alice"), "alice-project");
        testData.user("bob");
    }

    @Test
    void anonymousUsersAreSentToLogin() throws Exception {
        mvc.perform(get("/projects"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @WithMockUser(username = "alice")
    void ownerCanViewProject() throws Exception {
        mvc.perform(get("/projects/" + aliceProject.getId())).andExpect(status().isOk());
        mvc.perform(get("/projects/" + aliceProject.getId() + "/events")).andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "bob")
    void otherUsersGet404() throws Exception {
        mvc.perform(get("/projects/" + aliceProject.getId())).andExpect(status().isNotFound());
        mvc.perform(get("/projects/" + aliceProject.getId() + "/events")).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "bob")
    void otherUsersCannotAddEndpoints() throws Exception {
        mvc.perform(post("/projects/" + aliceProject.getId() + "/endpoints").param("name", "x").with(csrf()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "alice")
    void formsRequireCsrf() throws Exception {
        mvc.perform(post("/projects").param("name", "no-csrf")).andExpect(status().isForbidden());
    }
}
