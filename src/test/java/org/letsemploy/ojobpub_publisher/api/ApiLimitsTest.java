package org.letsemploy.ojobpub_publisher.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.token.ServiceTokenService;
import org.letsemploy.ojobpub_publisher.token.TokenScope;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * The limits of spec 11.6, with the ceilings lowered so they can be reached.
 *
 * <p>The production numbers are deliberately out of reach of this schema - it has
 * no recursion, so nothing legitimate nests past four - which is why the limits
 * are exercised by configuration rather than by an enormous query. The point of
 * the test is that the ceilings are wired in at all.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
@TestPropertySource(properties = {
        "app.api.max-query-depth=2",
        "app.api.rate-limit=2",
        "app.api.max-request-bytes=64"
})
@Transactional
class ApiLimitsTest {

    private static final String EMPLOYER = "003d6aec-021b-11f1-aefa-f649a5d91690";
    private static final String DEV_ADMIN = "11111111-1111-4111-8111-111111111111";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ServiceTokenService tokens;
    @Autowired
    private ObjectMapper json;

    private String secret() {
        Actor devAdmin = Actor.user(UUID.fromString(DEV_ADMIN), "dev@localhost", "dev@localhost",
                true, Map.of(UUID.fromString(EMPLOYER), MembershipRole.OWNER));
        return tokens.create(UUID.fromString(EMPLOYER), "limits-" + UUID.randomUUID(),
                MembershipRole.OWNER, Set.of(TokenScope.JOBS_READ, TokenScope.FEEDS_READ),
                devAdmin).getSecret();
    }

    private org.springframework.mock.web.MockHttpServletResponse send(String secret, String document)
            throws Exception {
        return mvc.perform(post("/graphql")
                        .header("Authorization", "Bearer " + secret)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("query", document))))
                .andReturn().getResponse();
    }

    private String code(String body) throws Exception {
        return json.readTree(body).path("errors").path(0).path("extensions").path("code").asText();
    }

    @Test
    void anOverDeepQueryIsRefused() throws Exception {
        String secret = secret();
        // Depth 2 is allowed, depth 3 is not.
        assertThat(json.readTree(send(secret, "{ employer { name } }").getContentAsString())
                .has("errors")).isFalse();
        assertThat(code(send(secret, "{ employer { headquarters { city } } }").getContentAsString()))
                .isEqualTo("LIMIT_EXCEEDED");
    }

    @Test
    void theBudgetRunsOutAndSaysWhenItReturns() throws Exception {
        String secret = secret();
        String document = "{ employer { name } }";
        send(secret, document);
        send(secret, document);
        var refused = send(secret, document);
        assertThat(refused.getStatus()).isEqualTo(429);
        assertThat(code(refused.getContentAsString())).isEqualTo("RATE_LIMITED");
        assertThat(refused.getHeader("X-RateLimit-Remaining")).isEqualTo("0");
        assertThat(refused.getHeader("Retry-After")).isNotNull();
    }

    @Test
    void anOversizedBodyIsRefusedBeforeItIsParsed() throws Exception {
        var refused = send(secret(), "{ jobs(size: 50) { content { id title description } } }");
        assertThat(refused.getStatus()).isEqualTo(413);
        assertThat(code(refused.getContentAsString())).isEqualTo("REQUEST_TOO_LARGE");
    }

    /** Array batching turns one rate-limit unit into many, so it is not accepted. */
    @Test
    void batchedOperationsAreRejected() throws Exception {
        int status = mvc.perform(post("/graphql")
                        .header("Authorization", "Bearer " + secret())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"query\":\"{ employer { name } }\"}]"))
                .andReturn().getResponse().getStatus();
        assertThat(status).isNotEqualTo(200);
    }
}
