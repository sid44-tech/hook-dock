package dev.hookdock.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import dev.hookdock.TestData;
import dev.hookdock.endpoints.CreatedEndpoint;
import dev.hookdock.endpoints.EndpointService;
import dev.hookdock.projects.Project;

/**
 * The "Recent rejections" section on the dashboard. Each test uses its own
 * users and endpoints; the rejection log is shared across the test context,
 * which also checks that only the signed-in user's rejections are shown.
 */
@SpringBootTest
@AutoConfigureMockMvc
class DashboardRejectionsTest {

    @Autowired
    MockMvc mvc;

    @Autowired
    TestData testData;

    @Autowired
    EndpointService endpointService;

    @Value("${hookdock.rate-limit.requests}")
    int rateLimit;

    @Test
    void showsRateLimitedRequestsAcrossTheUsersEndpoints() throws Exception {
        var owner = testData.user("rejections-owner");
        Project first = testData.project(owner, "first-project");
        Project second = testData.project(owner, "second-project");
        CreatedEndpoint github = testData.endpoint(first, "github-hook");
        CreatedEndpoint stripe = testData.endpoint(second, "stripe-hook");
        endpointService.setRateLimit(stripe.endpoint().getId(), 2);

        flood(github, rateLimit + 1);
        flood(stripe, 2 + 1);

        String page = dashboard("rejections-owner");
        String rejections = section(page, "Recent rejections", "Recent delivery failures");
        assertThat(rejections)
                .contains("first-project", "github-hook", rateLimit + " / min")
                .contains("second-project", "stripe-hook", "2 / min")
                .contains("Rate limited", "POST")
                .doesNotContain("No rejected requests.");
    }

    @Test
    void otherUsersDoNotSeeYourRejections() throws Exception {
        var victim = testData.user("rejections-victim");
        CreatedEndpoint endpoint = testData.endpoint(testData.project(victim, "victim-project"), "victim-hook");
        flood(endpoint, rateLimit + 1);

        var other = testData.user("rejections-other");
        testData.endpoint(testData.project(other, "other-project"), "other-hook");

        String page = dashboard("rejections-other");
        assertThat(page).doesNotContain("victim-hook", "victim-project");
        assertThat(section(page, "Recent rejections", "Recent delivery failures")).contains("No rejected requests.");
    }

    @Test
    void capturedRequestsAreNotRejections() throws Exception {
        var owner = testData.user("rejections-none");
        CreatedEndpoint endpoint = testData.endpoint(testData.project(owner, "calm-project"), "calm-hook");
        flood(endpoint, rateLimit);

        String page = dashboard("rejections-none");
        assertThat(section(page, "Recently captured", "Recent rejections")).contains("calm-hook");
        assertThat(section(page, "Recent rejections", "Recent delivery failures"))
                .contains("No rejected requests.")
                .doesNotContain("calm-hook");
    }

    @Test
    void sectionSitsBetweenRecentlyCapturedAndDeliveryFailures() throws Exception {
        testData.user("rejections-layout");
        String page = dashboard("rejections-layout");

        int captured = page.indexOf("Recently captured");
        int rejections = page.indexOf("Recent rejections");
        int failures = page.indexOf("<h2 class=\"h5 mt-4\">Recent delivery failures");
        assertThat(captured).isPositive();
        assertThat(rejections).isGreaterThan(captured);
        assertThat(failures).isGreaterThan(rejections);
    }

    @Test
    void unknownTokensAreNotShownToAnyone() throws Exception {
        for (int i = 0; i < rateLimit + 2; i++) {
            mvc.perform(post("/in/no-such-token").content("{}")).andExpect(status().isNotFound());
        }
        testData.endpoint(testData.project(testData.user("rejections-bystander"), "bystander"), "hook");

        mvc.perform(get("/").with(user("rejections-bystander")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("No rejected requests.")))
                .andExpect(content().string(not(containsString("no-such-token"))));
    }

    @Test
    void rejectedCardCountsTheUsersRejectionsAcrossEndpoints() throws Exception {
        var owner = testData.user("card-owner");
        CreatedEndpoint first = testData.endpoint(testData.project(owner, "card-one"), "card-hook-1");
        CreatedEndpoint second = testData.endpoint(testData.project(owner, "card-two"), "card-hook-2");

        assertThat(rejectedCard(dashboard("card-owner"))).isEqualTo("0");

        // More than the 10 the list keeps for one endpoint, to show the card counts them all.
        flood(first, rateLimit + 12);
        flood(second, rateLimit + 3);

        assertThat(rejectedCard(dashboard("card-owner"))).isEqualTo("15");
    }

    @Test
    void rejectedCardIgnoresOtherUsersRejections() throws Exception {
        var busy = testData.user("card-busy");
        flood(testData.endpoint(testData.project(busy, "busy"), "busy-hook"), rateLimit + 4);

        var quiet = testData.user("card-quiet");
        testData.endpoint(testData.project(quiet, "quiet"), "quiet-hook");

        assertThat(rejectedCard(dashboard("card-quiet"))).isEqualTo("0");
        assertThat(rejectedCard(dashboard("card-busy"))).isEqualTo("4");
    }

    @Test
    void dashboardHasFiveCardsInOneRow() throws Exception {
        testData.user("card-layout");
        String page = dashboard("card-layout");

        assertThat(page).contains("row-cols-md-5");
        String cards = page.substring(page.indexOf("row-cols-md-5"), page.indexOf(">Recently captured</h2>"));
        assertThat(cards).containsSubsequence(
                "Projects", "Endpoints", "Captured events", "Recent delivery failures", "Rejected (24h)");
    }

    // The number shown on the "Rejected (24h)" card.
    private static String rejectedCard(String page) {
        int label = page.indexOf(">Rejected (24h)</div>");
        assertThat(label).isPositive();
        Matcher m = Pattern.compile("<div class=\"fs-4\">([^<]*)</div>")
                .matcher(page);
        assertThat(m.find(label)).isTrue();
        return m.group(1).trim();
    }

    private void flood(CreatedEndpoint endpoint, int requests) throws Exception {
        for (int i = 0; i < requests; i++) {
            mvc.perform(post("/in/" + endpoint.token()).content("{}"));
        }
    }

    private String dashboard(String username) throws Exception {
        return mvc.perform(get("/").with(user(username)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    // The HTML between two section headings.
    private static String section(String page, String heading, String nextHeading) {
        int start = page.indexOf(">" + heading + "</h2>");
        int end = page.indexOf(">" + nextHeading + "</h2>", start);
        assertThat(start).as("heading '%s'", heading).isPositive();
        assertThat(end).as("heading '%s'", nextHeading).isGreaterThan(start);
        return page.substring(start, end);
    }
}
