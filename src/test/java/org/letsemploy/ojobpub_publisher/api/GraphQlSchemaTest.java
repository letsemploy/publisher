package org.letsemploy.ojobpub_publisher.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * The management API's schema is published as SDL, anonymously, while the API
 * itself still takes a token (spec 11.2, 11.6). Run without the dev bypass, so
 * the real chains decide.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.WithoutDev.class)
class GraphQlSchemaTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void theSchemaIsPublicAndOpensNoSession() throws Exception {
        MvcResult result = mvc.perform(get("/graphql/schema").param("lang", "de"))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .contains("type Query")
                .contains("createJob");
        assertThat(result.getRequest().getSession(false)).as("session").isNull();
        assertThat(result.getResponse().getHeader("Set-Cookie")).as("cookie").isNull();
    }

    @Test
    void theApiStillTakesAToken() throws Exception {
        mvc.perform(post("/graphql")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"{ __typename }\"}"))
                .andExpect(status().isUnauthorized());
    }
}
