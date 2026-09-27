package org.letsemploy.ojobpub_publisher.employer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.feed.Feed;
import org.letsemploy.ojobpub_publisher.feed.FeedService;
import org.letsemploy.ojobpub_publisher.location.LocationRepo;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.CurrentUserService;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.letsemploy.ojobpub_publisher.token.TokenScope;
import org.letsemploy.ojobpub_publisher.web.EmployerContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deleting an employer (spec 2.7, 7.13): its owners may, and admins; nobody else
 * learns it exists; the name must be typed back; and everything it held goes -
 * its public feed URL included.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.class)
@Transactional
class EmployerDeleteTest {

    /** Acme, from the seed: the employer the owner below is not a member of. */
    private static final UUID ACME = UUID.fromString("003d6aec-021b-11f1-aefa-f649a5d91690");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private EmployerService employerService;
    @Autowired
    private EmployerRepo employerRepo;
    @Autowired
    private FeedService feedService;
    @Autowired
    private LocationRepo locationRepo;
    @Autowired
    private UserRepo userRepo;
    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private CurrentUserService currentUserService;

    private Employer doomed;
    private Actor owner;

    @BeforeEach
    void anOwnerWithAnEmployerOfTheirOwn() {
        UserEntity user = new UserEntity();
        user.setIssuer("test");
        user.setSubject("owner-" + UUID.randomUUID());
        user.setDisplayName("Owen Owner");
        user.setRole(UserEntity.Role.USER);
        user = userRepo.save(user);
        doomed = employerService.save(null, "Doomed Ltd", null, null, null,
                Headquarters.newLocation("Winterthur", "CH"),
                Actor.user(user.getId(), "Owen Owner", null, false, Map.of()));
        feedService.createDefaultFeed(doomed);
        owner = Actor.user(user.getId(), "Owen Owner", null, false,
                Map.of(doomed.getId(), MembershipRole.OWNER));
        actAs(owner);
    }

    private void actAs(Actor actor) {
        given(currentUserService.isDevMode()).willReturn(true);
        given(currentUserService.current()).willReturn(actor);
    }

    private String feedUrl() {
        Feed all = feedService.findByEmployer(doomed.getId()).get(0);
        return "/ojobpub/v1/" + doomed.getSlug() + "_" + doomed.getId() + "/" + all.getSlug() + "_" + all.getId()
                + "/ojobpub.json";
    }

    /** What goes, spelled out, and the name to type back. */
    @Test
    void theConfirmationSaysWhatGoesAndAsksForTheName() throws Exception {
        String modal = mvc.perform(get("/employers/" + doomed.getId() + "/delete"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(modal)
                .contains("Delete Doomed Ltd?")
                .contains("no jobs")
                .contains("1 feed and its public URL")
                .contains("1 member, who loses access")
                .contains("public feed URLs")
                .contains("data-confirm-name=\"Doomed Ltd\"")
                .contains("name=\"confirmName\"");
    }

    @Test
    void anOwnerDeletesItAndEverythingGoes() throws Exception {
        String feed = feedUrl();
        mvc.perform(get(feed)).andExpect(status().isOk());

        mvc.perform(post("/employers/" + doomed.getId() + "/delete").param("confirmName", "Doomed Ltd"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/employers"))
                .andExpect(flash().attribute("successMsg", "employer.deleted"));
        entityManager.flush();
        entityManager.clear();

        assertThat(employerRepo.findById(doomed.getId())).isEmpty();
        assertThat(locationRepo.findByEmployerIdOrderByCityAsc(doomed.getId())).isEmpty();
        assertThat(feedService.findByEmployer(doomed.getId())).isEmpty();
        // The consumers' URL is gone with it: the price the confirmation named.
        mvc.perform(get(feed)).andExpect(status().isNotFound());
    }

    /** A near miss deletes nothing: the name is checked by the server, not the button. */
    @Test
    void aWrongNameDeletesNothing() throws Exception {
        mvc.perform(post("/employers/" + doomed.getId() + "/delete").param("confirmName", "doomed ltd"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/employers/" + doomed.getId()))
                .andExpect(flash().attribute("errorMsg", "employer.delete.nameMismatch"));
        mvc.perform(post("/employers/" + doomed.getId() + "/delete"))
                .andExpect(flash().attribute("errorMsg", "employer.delete.nameMismatch"));
        assertThat(employerRepo.findById(doomed.getId())).isPresent();
    }

    /** An editor works on content, not on the workspace: told it does not exist. */
    @Test
    void anEditorMayNot() throws Exception {
        actAs(Actor.user(UUID.randomUUID(), "Eddie Editor", null, false,
                Map.of(doomed.getId(), MembershipRole.EDITOR)));
        mvc.perform(get("/employers/" + doomed.getId() + "/delete")).andExpect(status().isNotFound());
        mvc.perform(post("/employers/" + doomed.getId() + "/delete").param("confirmName", "Doomed Ltd"))
                .andExpect(status().isNotFound());
        assertThat(employerRepo.findById(doomed.getId())).isPresent();
    }

    /** And an owner of one employer is nobody to another. */
    @Test
    void anOwnerCannotDeleteSomeoneElsesEmployer() throws Exception {
        mvc.perform(post("/employers/" + ACME + "/delete").param("confirmName", "Acme AG"))
                .andExpect(status().isNotFound());
        assertThat(employerRepo.findById(ACME)).isPresent();
    }

    /** Platform staff may delete any employer, as before (spec 2.1). */
    @Test
    void anAdminMay() throws Exception {
        actAs(Actor.user(UUID.randomUUID(), "Ada Admin", null, true, Map.of()));
        mvc.perform(post("/employers/" + doomed.getId() + "/delete").param("confirmName", "Doomed Ltd"))
                .andExpect(redirectedUrl("/employers"));
        assertThat(employerRepo.findById(doomed.getId())).isEmpty();
    }

    /** A credential never may, even with the owner role: not through any path. */
    @Test
    void aServiceTokenNeverMay() {
        Actor token = Actor.serviceToken(UUID.randomUUID(), "ci", doomed.getId(), MembershipRole.OWNER,
                Set.of(TokenScope.PEOPLE_WRITE));
        assertThatThrownBy(() -> employerService.delete(doomed.getId(), "Doomed Ltd", token))
                .isInstanceOf(NotFoundException.class);
        assertThat(employerRepo.findById(doomed.getId())).isPresent();
    }

    /** Working on the deleted employer would scope every screen to nothing. */
    @Test
    void theActiveEmployerIsLetGoOf() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/context/employer").session(session).param("employerId", doomed.getId().toString()));
        mvc.perform(post("/employers/" + doomed.getId() + "/delete").session(session)
                .param("confirmName", "Doomed Ltd"));
        UUID active = Collections.list(session.getAttributeNames()).stream()
                .map(session::getAttribute).filter(EmployerContext.class::isInstance)
                .map(EmployerContext.class::cast).findFirst()
                .map(EmployerContext::getActiveEmployerId).orElse(null);
        assertThat(active).isNull();
    }
}
