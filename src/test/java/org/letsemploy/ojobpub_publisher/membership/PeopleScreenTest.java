package org.letsemploy.ojobpub_publisher.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.CurrentUserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * The People screen seen by an editor (spec 7.18).
 *
 * <p>The development bypass always resolves to the seeded admin, so the only way
 * to render this screen as somebody else is to override who the current actor is.
 * That is the whole point of the test: every other screen test in this project
 * runs as an admin, for whom the owner-only half is always present, so nothing
 * else would notice if an editor were shown the invite form - or shown nothing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
class PeopleScreenTest {

    /** Fixed ids from data.sql: Acme, and Mara Member, an editor of it. */
    private static final String EMPLOYER = "003d6aec-021b-11f1-aefa-f649a5d91690";
    private static final UUID MEMBER = UUID.fromString("44444444-4444-4444-8444-444444444444");

    @Autowired
    private MockMvc mvc;

    @MockitoBean
    private CurrentUserService currentUserService;

    @BeforeEach
    void actAsAnEditor() {
        given(currentUserService.isDevMode()).willReturn(true);
        given(currentUserService.current()).willReturn(Actor.user(
                MEMBER, "Mara Member", "member@example.com", false,
                Map.of(UUID.fromString(EMPLOYER), MembershipRole.EDITOR)));
    }

    private String people(String path) throws Exception {
        return mvc.perform(get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** Name, email and role - the membership itself (spec 2.1, 7.18). */
    @Test
    void anEditorSeesWhoBelongsAndWithWhichRole() throws Exception {
        assertThat(people("/people"))
                .contains("Mara Member")
                .contains("member@example.com")
                .contains("dev@localhost")
                .contains("Editor")
                .contains("Owner");
    }

    /**
     * The owner-only half is absent, not disabled: a greyed-out invite form
     * advertises a capability and then refuses it, and pending invitations are
     * the owner's to disclose (spec 7.18).
     */
    @Test
    void anEditorGetsNoneOfTheOwnerControls() throws Exception {
        String body = people("/people");
        assertThat(body)
                .doesNotContain("Send invitation")
                .doesNotContain("Pending invitations")
                .doesNotContain("Make owner")
                .doesNotContain("Make editor")
                // the pending invitee must not leak through the invitations panel
                .doesNotContain("editor@example.com");
    }

    /** A missing control with no explanation reads as a broken screen. */
    @Test
    void anEditorIsToldWhyTheControlsAreMissing() throws Exception {
        assertThat(people("/people")).contains("ask one of the owners");
    }

    /** The same screen, reached from the employer record rather than the sidebar. */
    @Test
    void bothRoutesRenderTheSameScreenForAnEditor() throws Exception {
        assertThat(people("/employers/" + EMPLOYER + "/people"))
                .contains("Mara Member")
                .doesNotContain("Send invitation");
    }

    /**
     * Hiding the controls is presentation; the refusal is in the service. An
     * editor posting the invite directly is refused as a non-owner, and with 404
     * rather than 403 (spec 2.4).
     */
    @Test
    void anEditorCannotInviteByPostingDirectly() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .post("/employers/" + EMPLOYER + "/people/invite")
                        .param("email", "someone@example.com")
                        .param("role", "EDITOR"))
                .andExpect(status().isNotFound());
    }

    /** A non-member is told the employer does not exist, never that it is forbidden. */
    @Test
    void aNonMemberCannotSeeAnEmployersPeople() throws Exception {
        given(currentUserService.current()).willReturn(Actor.user(
                UUID.randomUUID(), "Outsider", "outsider@example.com", false, Map.of()));
        mvc.perform(get("/employers/" + EMPLOYER + "/people"))
                .andExpect(status().isNotFound());
    }

    /**
     * With no employer to name, the screen says so rather than silently picking
     * one (spec 7.18).
     */
    @Test
    void aUserWithNoEmployerIsToldToChooseOne() throws Exception {
        given(currentUserService.current()).willReturn(Actor.user(
                UUID.randomUUID(), "Newcomer", "newcomer@example.com", false, Map.of()));
        assertThat(people("/people"))
                .contains("No employer selected")
                .doesNotContain("Mara Member");
    }
}
