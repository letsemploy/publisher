package org.letsemploy.ojobpub_publisher.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.employer.EmployerService;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.token.ServiceTokenService;
import org.letsemploy.ojobpub_publisher.token.TokenScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;

/**
 * The management API end to end (spec 11).
 *
 * <p>The properties that matter here are the ones a second implementation would
 * quietly lose: the employer comes from the token and not the request, a domain
 * refusal is data rather than a fault, and the invite form is not an oracle.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
// Quotas off: this class tests API semantics, not limits, and it is not
// @Transactional - a run killed before @AfterEach leaves test-% tokens behind,
// and the next run would then fail on a quota that has nothing to do with what
// is being tested (spec 8.4; 0 means unlimited).
@TestPropertySource(properties = {
        "app.limits.tokens-per-employer=0",
        "app.limits.memberships-per-user=0"})
// Deliberately NOT @Transactional: a test transaction would hold every write open
// and hide the case this suite most needs to see - a service marking the caller's
// transaction rollback-only when it refuses. What the tests create is removed in
// @AfterEach instead, so the shared database stays as the seed left it.
class ManagementApiTest {

    /** Fixed ids from data.sql. */
    private static final String EMPLOYER = "003d6aec-021b-11f1-aefa-f649a5d91690";
    private static final String DEV_ADMIN = "11111111-1111-4111-8111-111111111111";
    private static final String JOB_INCOMPLETE = "de69390e-133f-4b2f-e094-8f6071829301";
    /** A draft with no description and no location, so activation must be refused. */
    private static final String JOB_DRAFT = "bc47178c-f11d-490d-ce72-6d4e5f607182";
    private static final String HEADQUARTERS = "2c2e59d5-0b1a-11f1-938c-42a3421a666f";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ServiceTokenService tokens;
    @Autowired
    private EmployerService employers;
    @Autowired
    private ObjectMapper json;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private org.letsemploy.ojobpub_publisher.token.ServiceTokenRepo tokenRepo;

    /** Employers cascade to their feeds, memberships and tokens. */
    @AfterEach
    void removeWhatTheseTestsCreated() {
        jdbc.update("DELETE FROM jobs WHERE title = 'API written'");
        jdbc.update("DELETE FROM service_tokens WHERE name LIKE 'test-%' OR name = 'foreign'");
        jdbc.update("DELETE FROM employers WHERE name LIKE 'Other Co %'");
    }

    private Actor devAdmin() {
        return Actor.user(UUID.fromString(DEV_ADMIN), "dev@localhost", "dev@localhost", true,
                Map.of(UUID.fromString(EMPLOYER), MembershipRole.OWNER));
    }

    private String tokenWith(TokenScope... scopes) {
        return tokens.create(UUID.fromString(EMPLOYER), "test-" + UUID.randomUUID(),
                MembershipRole.OWNER, Set.of(scopes), devAdmin()).getSecret();
    }

    private JsonNode query(String secret, String document) throws Exception {
        String body = json.writeValueAsString(Map.of("query", document));
        String response = mvc.perform(post("/graphql")
                        .header("Authorization", "Bearer " + secret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response);
    }

    private static String firstErrorCode(JsonNode response) {
        return response.path("errors").path(0).path("extensions").path("code").asText();
    }

    // ------------------------------------------------------------ transport

    @Test
    void noTokenIsUnauthenticated() throws Exception {
        String response = mvc.perform(post("/graphql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"{ employer { name } }\"}"))
                .andReturn().getResponse().getContentAsString();
        assertThat(firstErrorCode(json.readTree(response))).isEqualTo("UNAUTHENTICATED");
    }

    @Test
    void anUnknownTokenIsIndistinguishableFromARevokedOne() throws Exception {
        assertThat(firstErrorCode(query("ojp_nosuchtoken.whatever", "{ employer { name } }")))
                .isEqualTo("UNAUTHENTICATED");
    }

    /**
     * Expired joins unknown and revoked in giving one answer. If a lapsed token
     * answered differently, a caller holding a guessed prefix would learn that
     * the prefix exists - the oracle the test above exists to prevent.
     */
    @Test
    void anExpiredTokenIsIndistinguishableFromAnUnknownOne() throws Exception {
        String secret = tokenWith(TokenScope.JOBS_READ);
        String prefix = secret.substring(0, secret.indexOf('.'));
        // Set through the entity, not with a JDBC Timestamp: the database runs in
        // UTC and the host need not, and the driver converts a Timestamp with the
        // host's zone - which silently writes an expiry in the future.
        var token = tokenRepo.findByPrefix(prefix).orElseThrow();
        token.setExpiresAt(java.time.Instant.now().minusSeconds(60));
        tokenRepo.save(token);

        String expired = mvc.perform(post("/graphql")
                        .header("Authorization", "Bearer " + secret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"{ employer { name } }\"}"))
                .andReturn().getResponse().getContentAsString();
        String unknown = mvc.perform(post("/graphql")
                        .header("Authorization", "Bearer ojp_nosuchtoken.whatever")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"{ employer { name } }\"}"))
                .andReturn().getResponse().getContentAsString();
        assertThat(expired)
                .as("byte for byte, or it is not indistinguishable")
                .isEqualTo(unknown);
    }

    /** No GET execution: a query must not be triggerable by a link (spec 11.2). */
    @Test
    void getIsRefused() throws Exception {
        int status = mvc.perform(get("/graphql")
                        .header("Authorization", "Bearer " + tokenWith(TokenScope.JOBS_READ)))
                .andReturn().getResponse().getStatus();
        assertThat(status).isEqualTo(405);
    }

    @Test
    void everyAnswerPublishesTheRemainingBudget() throws Exception {
        var response = mvc.perform(post("/graphql")
                        .header("Authorization", "Bearer " + tokenWith(TokenScope.JOBS_READ))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"{ employer { name } }\"}"))
                .andReturn().getResponse();
        assertThat(response.getHeader("X-RateLimit-Limit")).isNotNull();
        assertThat(response.getHeader("X-RateLimit-Remaining")).isNotNull();
    }

    // -------------------------------------------------------------- reading

    @Test
    void theEmployerComesFromTheTokenAndNotTheRequest() throws Exception {
        JsonNode response = query(tokenWith(TokenScope.JOBS_READ), "{ employer { id name } }");
        assertThat(response.path("data").path("employer").path("id").asText()).isEqualTo(EMPLOYER);
        assertThat(response.path("data").path("employer").path("name").asText()).isEqualTo("Acme AG");
    }

    @Test
    void jobsAreReadWithTheirPresentationNotJustTheirStoredStatus() throws Exception {
        JsonNode jobs = query(tokenWith(TokenScope.JOBS_READ),
                "{ jobs(size: 50) { totalElements content { id status presentation } } }")
                .path("data").path("jobs");
        assertThat(jobs.path("totalElements").asInt()).isGreaterThan(0);
        List<String> presentations = jobs.path("content").findValuesAsText("presentation");
        // The seed covers all five states; an ACTIVE job with no location presents
        // as INCOMPLETE, which is exactly what a stored status would not tell you.
        assertThat(presentations).contains("PUBLISHED", "INCOMPLETE");
    }

    /**
     * A token cannot name another employer, so the commonest authorization bug in
     * a multi-tenant API cannot be written (spec 11.2).
     */
    @Test
    void aTokenCannotReachAnotherEmployersJob() throws Exception {
        var other = employers.save(null, "Other Co " + UUID.randomUUID(), null, null, null,
                UUID.fromString(HEADQUARTERS), devAdmin());
        JsonNode response = query(tokenWith(TokenScope.JOBS_READ),
                "{ job(id: \"" + JOB_INCOMPLETE + "\") { id } }");
        assertThat(response.path("data").path("job").isNull()).isFalse();

        // The same query issued by a token of the other employer finds nothing.
        String foreign = tokens.create(other.getId(), "foreign", MembershipRole.OWNER,
                Set.of(TokenScope.JOBS_READ), devAdmin()).getSecret();
        assertThat(firstErrorCode(query(foreign, "{ job(id: \"" + JOB_INCOMPLETE + "\") { id } }")))
                .isEqualTo("NOT_FOUND");
    }

    /**
     * JobInput refers to locations and tags by id, so a caller that cannot list
     * them cannot write a job. The round trip is the test: read an id, then use it.
     */
    @Test
    void aJobCanBeWrittenFromIdsTheApiItselfHandsOut() throws Exception {
        String secret = tokenWith(TokenScope.JOBS_WRITE);
        String locationId = query(secret, "{ locations(q: \"ber\") { id city } }")
                .path("data").path("locations").path(0).path("id").asText();
        assertThat(locationId).isNotBlank();
        String tagId = query(secret, "{ tags(q: \"jav\") { id name } }")
                .path("data").path("tags").path(0).path("id").asText();
        assertThat(tagId).isNotBlank();

        JsonNode created = query(secret,
                "mutation { createJob(input: { title: \"API written\", "
                        + "url: \"https://www.acme.example/jobs/API-1\", language: \"en\", "
                        + "jobType: PERMANENT, locationIds: [\"" + locationId + "\"], "
                        + "tagIds: [\"" + tagId + "\"] }) "
                        + "{ job { id title presentation locations { city } tags } "
                        + "userErrors { field message } } }")
                .path("data").path("createJob");
        assertThat(created.path("userErrors")).isEmpty();
        assertThat(created.path("job").path("presentation").asText()).isEqualTo("DRAFT");
        assertThat(created.path("job").path("tags").findValuesAsText("")).isNotNull();
        assertThat(created.path("job").path("locations")).isNotEmpty();
    }

    // --------------------------------------------------------------- scopes

    @Test
    void aReadScopeDoesNotPermitWriting() throws Exception {
        JsonNode response = query(tokenWith(TokenScope.JOBS_READ),
                "mutation { deleteJob(id: \"" + JOB_INCOMPLETE + "\") { deletedId } }");
        assertThat(firstErrorCode(response)).isEqualTo("FORBIDDEN");
    }

    @Test
    void writingImpliesReading() throws Exception {
        JsonNode response = query(tokenWith(TokenScope.JOBS_WRITE), "{ jobs { totalElements } }");
        assertThat(response.has("errors")).isFalse();
    }

    @Test
    void aJobsTokenCannotReadPeople() throws Exception {
        assertThat(firstErrorCode(query(tokenWith(TokenScope.JOBS_WRITE), "{ members { email } }")))
                .isEqualTo("FORBIDDEN");
    }

    // --------------------------------------------------------------- errors

    /**
     * The separation of spec 11.4: a broken rule is an expected outcome of a
     * well-formed request, so it arrives with the payload, not in `errors`.
     */
    @Test
    void aRefusedActivationIsDataAndNotAFault() throws Exception {
        JsonNode response = query(tokenWith(TokenScope.JOBS_WRITE),
                "mutation { activateJob(id: \"" + JOB_DRAFT + "\") "
                        + "{ job { id } userErrors { field message code } } }");
        assertThat(response.has("errors")).isFalse();
        JsonNode payload = response.path("data").path("activateJob");
        assertThat(payload.path("job").isNull()).isTrue();
        assertThat(payload.path("userErrors")).isNotEmpty();
        // The code is stable and the message is the one the readiness panel shows,
        // resolved through the bundles rather than restated here (spec 11.4).
        assertThat(payload.path("userErrors").findValuesAsText("code")).contains("NOT_READY");
        assertThat(payload.path("userErrors").findValuesAsText("field")).contains("locationIds");
        assertThat(payload.path("userErrors").findValuesAsText("message"))
                .contains("At least one location");
    }

    @Test
    void everyFaultCarriesAStableCode() throws Exception {
        JsonNode response = query(tokenWith(TokenScope.JOBS_READ), "{ notAField }");
        assertThat(firstErrorCode(response)).isEqualTo("VALIDATION_ERROR");
    }

    // ---------------------------------------------------------------- people

    /**
     * The API must not become an oracle for which addresses are registered
     * (spec 2.6), so an address matching no account answers exactly as a real
     * invitation does.
     */
    @Test
    void invitingAnUnregisteredAddressIsIndistinguishableFromSuccess() throws Exception {
        String secret = tokenWith(TokenScope.PEOPLE_WRITE);
        JsonNode unknown = query(secret,
                "mutation { inviteMember(email: \"nobody-" + UUID.randomUUID()
                        + "@example.com\", role: EDITOR) { outcome userErrors { code } } }")
                .path("data").path("inviteMember");
        assertThat(unknown.path("outcome").asText()).isEqualTo("SENT");
        assertThat(unknown.path("userErrors")).isEmpty();
    }

    @Test
    void invitingAnExistingMemberIsReported() throws Exception {
        JsonNode response = query(tokenWith(TokenScope.PEOPLE_WRITE),
                "mutation { inviteMember(email: \"member@example.com\", role: EDITOR) "
                        + "{ outcome } }");
        assertThat(response.path("data").path("inviteMember").path("outcome").asText())
                .isEqualTo("ALREADY_MEMBER");
    }

    /** A token is never a person: the member list shows people only (spec 2.7). */
    @Test
    void theMemberListNamesPeopleAndNotTokens() throws Exception {
        JsonNode members = query(tokenWith(TokenScope.PEOPLE_READ), "{ members { email role } }")
                .path("data").path("members");
        assertThat(members.findValuesAsText("email")).contains("member@example.com");
        assertThat(members.toString()).doesNotContain("ojp_");
    }
}
