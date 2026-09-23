package org.letsemploy.ojobpub_publisher.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.token.ServiceTokenService;
import org.letsemploy.ojobpub_publisher.token.TokenScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A quota refused through the management API (spec 8.4, 11.4).
 *
 * <p>Not {@code @Transactional}, for the reason {@code ManagementApiTest}
 * documents: a service that refuses marks the caller's transaction rollback-only
 * and the commit would turn the refusal into a fault. Nothing is written here
 * anyway — the point of the test is that the write is refused.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.class)
@TestPropertySource(properties = {
        "app.limits.jobs-per-employer=6",
        "app.limits.tokens-per-employer=0",
        "app.limits.memberships-per-user=0"})
class ApiQuotaTest {

    private static final String EMPLOYER = "003d6aec-021b-11f1-aefa-f649a5d91690";
    private static final String DEV_ADMIN = "11111111-1111-4111-8111-111111111111";

    @Autowired private MockMvc mvc;
    @Autowired private org.springframework.jdbc.core.JdbcTemplate jdbc;
    @Autowired private ServiceTokenService tokens;
    @Autowired private ObjectMapper json;

    /**
     * Not @Transactional, so the token minted below would otherwise survive the
     * run and count against every quota-sensitive test that follows it.
     */
    @org.junit.jupiter.api.AfterEach
    void removeTheTokenThisTestMinted() {
        jdbc.update("DELETE FROM service_tokens WHERE name LIKE 'quota-%'");
    }

    /**
     * A quota is an expected outcome of a well-formed request, so it arrives with
     * the payload and not in the errors array.
     */
    @Test
    void aQuotaRefusalIsDataWithAStableCodeAndNoField() throws Exception {
        String secret = tokens.create(UUID.fromString(EMPLOYER), "quota-" + UUID.randomUUID(),
                MembershipRole.OWNER, Set.of(TokenScope.JOBS_WRITE),
                Actor.user(UUID.fromString(DEV_ADMIN), "dev@localhost", "dev@localhost", true,
                        Map.of(UUID.fromString(EMPLOYER), MembershipRole.OWNER))).getSecret();

        String document = "mutation { createJob(input: { title: \"Over the cap\", "
                + "url: \"https://www.acme.example/jobs/over\", language: \"en\", "
                + "jobType: PERMANENT }) { job { id } userErrors { field message code } } }";
        String body = mvc.perform(post("/graphql")
                        .header("Authorization", "Bearer " + secret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("query", document))))
                .andReturn().getResponse().getContentAsString();
        JsonNode response = json.readTree(body);

        assertThat(response.has("errors")).as("a refusal is data, not a fault").isFalse();
        JsonNode payload = response.path("data").path("createJob");
        assertThat(payload.path("job").isNull()).isTrue();

        JsonNode error = payload.path("userErrors").path(0);
        assertThat(error.path("code").asText())
                .as("distinct from the API's own LIMIT_EXCEEDED, which is a transport fault")
                .isEqualTo("QUOTA_REACHED");
        assertThat(error.path("field").isNull())
                .as("no input argument fixes being at a quota")
                .isTrue();
        assertThat(error.path("message").asText())
                .as("resolved through the bundles, not the service's English literal")
                .isEqualTo("This employer has reached its limit on job postings. "
                        + "Delete one to add another.");
    }
}
