package org.letsemploy.ojobpub_publisher.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * Every create and edit form is one template, opened as a modal from its button
 * and as a page when asked for directly (spec 7.4, 7.7).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.class)
@Transactional
class FormModalTest {

    /** Fixed ids from the seed. */
    private static final String ACME = "003d6aec-021b-11f1-aefa-f649a5d91690";
    private static final String JAVA_TAG = "1";

    @Autowired
    private MockMvc mvc;

    private static MockHttpServletRequestBuilder fromButton(MockHttpServletRequestBuilder request) {
        return request.header("HX-Request", "true");
    }

    private String body(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/jobs/create", "/feeds/create", "/feeds/permalinks/create", "/employers/create",
            "/tags/create", "/locations/create", "/tags/" + JAVA_TAG + "/update", "/employers/" + ACME + "/update",
            "/employers/" + ACME + "/tokens/create"})
    void aButtonOpensTheFormAsAModal(String path) throws Exception {
        String html = body(fromButton(get(path)));
        assertThat(html).contains("class=\"modal ").contains("hx-post=\"").contains("data-action=\"dismiss-modal\"")
                .doesNotContain("<html").doesNotContain("id=\"main\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"/jobs/create", "/feeds/create", "/feeds/permalinks/create", "/employers/create",
            "/tags/create", "/locations/create", "/employers/" + ACME + "/tokens/create"})
    void theUrlAskedForDirectlyIsTheWholePage(String path) throws Exception {
        String html = body(get(path));
        assertThat(html).contains("<html").contains("class=\"card-footer")
                .doesNotContain("class=\"modal ").doesNotContain("hx-post=\"");
        // The fields render once, inside the page's form - not again outside it.
        assertThat(html.split("name=\"" + firstField(path) + "\"", -1)).hasSize(2);
    }

    private static String firstField(String path) {
        return switch (path) {
            case "/jobs/create" -> "title";
            case "/locations/create" -> "city";
            default -> "name";
        };
    }

    @Test
    void aRefusalComesBackAsTheModalWithItsErrorsAndValues() throws Exception {
        String html = body(fromButton(post("/tags/create").param("name", "x".repeat(40))));
        assertThat(html).contains("class=\"modal ").contains("is-invalid").contains("x".repeat(40))
                .doesNotContain("<html");
    }

    @Test
    void aSuccessLeavesTheModalForTheNextPageWithItsFlash() throws Exception {
        String name = "modal-" + UUID.randomUUID().toString().substring(0, 8);
        mvc.perform(fromButton(post("/tags/create").param("name", name)))
                .andExpect(header().string("HX-Redirect", "/tags"))
                .andExpect(flash().attribute("successMsg", "msg.success.saved"));
    }

    /**
     * A token's secret is shown once, on the list the modal leaves for: it must
     * travel as a flash attribute through HX-Redirect, never in the URL (spec 2.8).
     */
    @Test
    void aTokenCreatedInTheModalShowsItsSecretOnTheList() throws Exception {
        String name = "modal-" + UUID.randomUUID().toString().substring(0, 8);
        String location = mvc.perform(fromButton(post("/employers/" + ACME + "/tokens")
                        .param("name", name).param("role", "EDITOR").param("scopes", "JOBS_READ")))
                .andExpect(status().isOk())
                .andExpect(flash().attributeExists("createdSecret"))
                .andReturn().getResponse().getHeader("HX-Redirect");
        assertThat(location).isIn("/tokens", "/employers/" + ACME + "/tokens");
    }

    @Test
    void aRefusedTokenKeepsItsRoleAndScopes() throws Exception {
        String html = body(fromButton(post("/employers/" + ACME + "/tokens")
                .param("name", "").param("role", "OWNER").param("scopes", "JOBS_WRITE")));
        assertThat(html).contains("class=\"modal ")
                .containsPattern("value=\"OWNER\"\\s+selected")
                .containsPattern("value=\"JOBS_WRITE\"\\s+id=\"scope-jobs_write\" checked");
    }

    @Test
    void aBoostedPostFromThePageStillRedirects() throws Exception {
        String name = "page-" + UUID.randomUUID().toString().substring(0, 8);
        mvc.perform(post("/tags/create").param("name", name).header("HX-Request", "true").header("HX-Boosted", "true"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/tags"));
    }
}
