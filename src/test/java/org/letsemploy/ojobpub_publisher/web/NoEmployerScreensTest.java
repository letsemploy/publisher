package org.letsemploy.ojobpub_publisher.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.CurrentUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The employer-scoped lists for a user who belongs to no employer (spec 2.5): they
 * say how to get one, and offer no create action - there is no employer for a new
 * record to belong to, so it could only answer 404.
 *
 * <p>Every other screen test runs as the seeded admin, who owns Acme, so none of
 * them would notice. A new account - from any provider or a local account - starts
 * exactly here (spec 2.2).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.class)
class NoEmployerScreensTest {

    /** Fixed ids from the seed: Acme, and Mara Member, an editor of it. */
    private static final UUID EMPLOYER = UUID.fromString("003d6aec-021b-11f1-aefa-f649a5d91690");
    private static final UUID MEMBER = UUID.fromString("44444444-4444-4444-8444-444444444444");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @BeforeEach
    void actAsANewcomer() {
        given(currentUserService.isDevMode()).willReturn(true);
        given(currentUserService.current()).willReturn(Actor.user(
                UUID.randomUUID(), "Newcomer", "newcomer@example.com", false, Map.of()));
    }

    private String render(String path) throws Exception {
        return mvc.perform(get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/locations", "/tags", "/jobs", "/feeds"})
    void aUserWithNoEmployerIsToldHowToGetOne(String list) throws Exception {
        assertThat(render(list))
                .contains("No employer yet")
                .contains("href=\"/employers/create\"")
                .doesNotContain("href=\"" + list + "/create\"");
    }

    /** The refusal itself is unchanged: the screens only stop offering it. */
    @ParameterizedTest
    @ValueSource(strings = {"/locations", "/tags", "/jobs", "/feeds"})
    void creatingWithoutAnEmployerIsStillNotFound(String list) throws Exception {
        mvc.perform(get(list + "/create")).andExpect(status().isNotFound());
    }

    /** The other direction: a member - an editor, not an admin - is offered the create action. */
    @ParameterizedTest
    @ValueSource(strings = {"/locations", "/tags", "/jobs", "/feeds"})
    void aMemberIsOfferedTheCreateAction(String list) throws Exception {
        given(currentUserService.current()).willReturn(Actor.user(
                MEMBER, "Mara Member", "member@example.com", false,
                Map.of(EMPLOYER, MembershipRole.EDITOR)));
        assertThat(render(list))
                .contains("href=\"" + list + "/create\"")
                .doesNotContain("No employer yet");
    }
}
