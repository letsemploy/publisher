package org.letsemploy.ojobpub_publisher.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * No identity provider and no development bypass: the back-office fails closed,
 * and what is public stays public (spec 2.4, 9.4).
 *
 * <p>The counterpart of {@link OidcLoginTest}. Between the two, a change that
 * picks the wrong chain in either direction - closed with a provider, or open
 * without one - fails a test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.WithoutDev.class)
class LockedChainTest {

    private static final String FEED = "/ojobpub/v1/acme-ag_003d6aec-021b-11f1-aefa-f649a5d91690"
            + "/all_cd58289d-022e-4a1e-df83-7e5f60718293/ojobpub.json";

    @Autowired
    private MockMvc mvc;

    /** Nobody can be authenticated, so nobody gets in - not even to a login page. */
    @Test
    void theBackOfficeIsClosed() throws Exception {
        mvc.perform(get("/")).andExpect(status().isForbidden());
        mvc.perform(get("/employers")).andExpect(status().isForbidden());
    }

    @Test
    void theFeedAndHealthStayAvailable() throws Exception {
        mvc.perform(get(FEED)).andExpect(status().isOk());
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
