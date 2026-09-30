package org.letsemploy.ojobpub_publisher.ojobpub.v1;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.feed.PermalinkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The published feed is anonymous and stateless (spec 5.1): no answer - served,
 * redirected, refused or failed - opens a session. It once did on every fetch,
 * because the back-office shell was built for the feed controller too.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.class)
class FeedStatelessTest {

    private static final String FEED = "/ojobpub/v1/acme-ag_003d6aec-021b-11f1-aefa-f649a5d91690"
            + "/all_cd58289d-022e-4a1e-df83-7e5f60718293/ojobpub.json";
    private static final String PERMALINK = "/ojobpub/v1/permalink/5a5a5a5a-5a5a-4a5a-8a5a-5a5a5a5a5a5a/ojobpub.json";

    @Autowired
    private MockMvc mvc;
    @MockitoSpyBean
    private PermalinkService permalinkService;

    private static void assertNoSession(MvcResult result) {
        assertThat(result.getRequest().getSession(false)).as("session").isNull();
        assertThat(result.getResponse().getHeader("Set-Cookie")).as("cookie").isNull();
    }

    @ParameterizedTest
    @CsvSource({
            FEED + ", 200",
            PERMALINK + ", 200",
            // A stale slug is nudged to the canonical URL.
            "/ojobpub/v1/old_003d6aec-021b-11f1-aefa-f649a5d91690/all_cd58289d-022e-4a1e-df83-7e5f60718293/ojobpub.json, 301",
            "/ojobpub/v1/permalink/00000000-0000-4000-8000-000000000000/ojobpub.json, 404",
            "/ojobpub/v1/permalink/not-a-uuid/ojobpub.json, 404",
    })
    void noAnswerOpensASession(String url, int expected) throws Exception {
        MvcResult result = mvc.perform(get(url)).andExpect(status().is(expected)).andReturn();
        assertNoSession(result);
    }

    /** A failure is JSON for the machine asking, not the back-office error page (spec 8.2). */
    @Test
    void aFailureIsJsonAndOpensNoSession() throws Exception {
        doThrow(new IllegalStateException("database gone")).when(permalinkService).findForPublishing(any());

        MvcResult result = mvc.perform(get(PERMALINK))
                .andExpect(status().isInternalServerError())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("internal_error"))
                .andExpect(jsonPath("$.reference").isNotEmpty())
                .andReturn();
        assertThat(result.getResponse().getContentAsString()).doesNotContain("database gone");
        assertNoSession(result);
    }
}
