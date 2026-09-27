package org.letsemploy.ojobpub_publisher.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.employer.EmployerRepo;
import org.letsemploy.ojobpub_publisher.invitation.InvitationRepo;
import org.letsemploy.ojobpub_publisher.invitation.InvitationStatus;
import org.letsemploy.ojobpub_publisher.tag.TagRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * An admin viewing the application as another user (spec 2.9, 7.20).
 *
 * <p>The real actor is the seeded admin - the dev bypass resolves to it - so this
 * exercises the real resolution, not a mock of it. The view must be exactly the
 * user's, and nothing may change while it lasts.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.class)
@Transactional
class ImpersonationTest {

    /** Fixed ids from the seed. */
    private static final String ACME = "003d6aec-021b-11f1-aefa-f649a5d91690";
    private static final UUID DEV_ADMIN = UUID.fromString("11111111-1111-4111-8111-111111111111");
    /** An editor of Acme. */
    private static final UUID MARA = UUID.fromString("44444444-4444-4444-8444-444444444444");
    /** No membership, one pending invitation to Acme. */
    private static final UUID EDITH = UUID.fromString("22222222-2222-4222-8222-222222222222");
    private static final UUID INVITATION = UUID.fromString("33333333-3333-4333-8333-333333333333");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepo userRepo;
    @Autowired
    private TagRepo tagRepo;
    @Autowired
    private EmployerRepo employerRepo;
    @Autowired
    private InvitationRepo invitationRepo;

    private MockHttpSession session;

    @BeforeEach
    void freshSession() {
        session = new MockHttpSession();
    }

    private String page(String path) throws Exception {
        return mvc.perform(get(path).session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private void viewAs(UUID user) throws Exception {
        mvc.perform(post("/users/" + user + "/impersonate").session(session))
                .andExpect(status().is3xxRedirection());
    }

    // ------------------------------------------------------------- the screen

    /** Everyone who has signed in, with "View as" - but not on oneself or an admin. */
    @Test
    void theUsersScreenListsEveryoneAndWhoCanBeViewedAs() throws Exception {
        String users = page("/users");
        assertThat(users).contains("Mara Member").contains("Edith Editor").contains("dev@localhost")
                .contains("/users/" + MARA + "/impersonate")
                .contains("/users/" + EDITH + "/impersonate")
                .doesNotContain("/users/" + DEV_ADMIN + "/impersonate");
        assertThat(page("/users?q=mara")).contains("Mara Member").doesNotContain("Edith Editor");
    }

    // --------------------------------------------------------------- viewing

    /** Exactly the user's view: their employer, their role, their sidebar. */
    @Test
    void viewingAsAnEditorShowsTheEditorsView() throws Exception {
        viewAs(MARA);

        String home = page("/");
        assertThat(home).contains("You are viewing as Mara Member").contains("member@example.com");
        String sidebar = home.substring(0, home.indexOf("</aside>"));
        assertThat(sidebar).doesNotContain("href=\"/users\"").doesNotContain("href=\"/tokens\"");

        // The editor's People screen: the list, and none of the owner's controls.
        String people = page("/employers/" + ACME + "/people");
        assertThat(people).contains("Mara Member").doesNotContain("Send invitation");
        // And no Users screen: the actor is Mara now, not an admin.
        mvc.perform(get("/users").session(session)).andExpect(status().isNotFound());
    }

    /** Stopping returns the admin to themselves. */
    @Test
    void stoppingReturnsToOneself() throws Exception {
        viewAs(MARA);
        mvc.perform(post("/impersonation/stop").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attribute("successMsg", "impersonation.stopped"));
        String home = page("/");
        assertThat(home).doesNotContain("You are viewing as");
        assertThat(home.substring(0, home.indexOf("</aside>"))).contains("href=\"/users\"");
    }

    // ------------------------------------------------------------- read-only

    @Test
    void nothingCanBeChangedWhileViewing() throws Exception {
        viewAs(MARA);
        long tags = tagRepo.count();
        long employers = employerRepo.count();

        mvc.perform(post("/tags/create").session(session).param("name", "sneaky"))
                .andExpect(status().isSeeOther())
                .andExpect(flash().attribute("errorMsg", "impersonation.readOnly"));
        mvc.perform(post("/employers/create").session(session)
                        .param("name", "Sneaky AG").param("hqCity", "Bern").param("hqCountry", "CH"))
                .andExpect(status().isSeeOther());

        assertThat(tagRepo.count()).isEqualTo(tags);
        assertThat(employerRepo.count()).isEqualTo(employers);
    }

    /**
     * Above all, no consent is given in someone else's name (spec 2.2): viewing as
     * Edith, her pending invitation cannot be accepted.
     */
    @Test
    void anInvitationCannotBeAcceptedOnSomeonesBehalf() throws Exception {
        viewAs(EDITH);
        assertThat(page("/invitations")).contains("Acme");

        mvc.perform(post("/invitations/" + INVITATION + "/accept").session(session))
                .andExpect(status().isSeeOther())
                .andExpect(flash().attribute("errorMsg", "impersonation.readOnly"));
        assertThat(invitationRepo.findById(INVITATION).orElseThrow().getStatus())
                .isEqualTo(InvitationStatus.PENDING);
    }

    /** What only changes the viewer's own view still works. */
    @Test
    void theThemeCanStillBeSwitched() throws Exception {
        viewAs(MARA);
        mvc.perform(post("/context/theme").session(session).param("theme", "dark"))
                .andExpect(status().is3xxRedirection())
                .andExpect(flash().attributeCount(0));
        assertThat(page("/")).contains("data-bs-theme=\"dark\"");
    }

    // ----------------------------------------------------------------- limits

    /** Never another admin, never oneself, never someone who does not exist: 404. */
    @Test
    void onlyOrdinaryUsersCanBeViewedAs() throws Exception {
        mvc.perform(post("/users/" + DEV_ADMIN + "/impersonate").session(session))
                .andExpect(status().isNotFound());
        mvc.perform(post("/users/" + UUID.randomUUID() + "/impersonate").session(session))
                .andExpect(status().isNotFound());
        assertThat(page("/")).doesNotContain("You are viewing as");
    }

    /** Viewing is not signing in: the viewed user's record is left exactly as it was. */
    @Test
    void viewingNeverRewritesTheViewedUser() throws Exception {
        UserEntity before = userRepo.findById(MARA).orElseThrow();
        String name = before.getDisplayName();
        String email = before.getEmail();

        viewAs(MARA);
        page("/");
        page("/jobs");

        UserEntity after = userRepo.findById(MARA).orElseThrow();
        assertThat(after.getDisplayName()).isEqualTo(name);
        assertThat(after.getEmail()).isEqualTo(email);
        assertThat(after.getRole()).isEqualTo(UserEntity.Role.USER);
    }
}
