package org.letsemploy.ojobpub_publisher.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.audit.AuditAction;
import org.letsemploy.ojobpub_publisher.audit.AuditEvent;
import org.letsemploy.ojobpub_publisher.audit.AuditRepo;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.employer.EmployerService;
import org.letsemploy.ojobpub_publisher.employer.Headquarters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Admin mode (spec 2.10): an admin works as an ordinary member until they switch
 * up, and every admin power follows the switch - because all of them read the one
 * flag it sets.
 *
 * <p>Runs with the application's default, not the test profile's: sessions start
 * in the user view. The real actor is the seeded admin, who owns Acme.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.class)
@TestPropertySource(properties = "app.admin.start-in-admin-mode=false")
@Transactional
class AdminModeTest {

    private static final UUID DEV_ADMIN = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final UUID MARA = UUID.fromString("44444444-4444-4444-8444-444444444444");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private UserRepo userRepo;
    @Autowired
    private EmployerService employerService;
    @Autowired
    private AuditRepo auditRepo;

    private MockHttpSession session;
    /** An employer the admin does not belong to. */
    private Employer elsewhere;

    @BeforeEach
    void setUp() {
        session = new MockHttpSession();
        UserEntity owner = new UserEntity();
        owner.setIssuer("test");
        owner.setSubject("elsewhere-" + UUID.randomUUID());
        owner.setDisplayName("Elsa Elsewhere");
        owner = userRepo.save(owner);
        elsewhere = employerService.save(null, "Elsewhere Ltd", null, null, null,
                Headquarters.newLocation("Basel", "CH"),
                Actor.user(owner.getId(), "Elsa Elsewhere", null, false, Map.of()));
    }

    private String page(String path) throws Exception {
        return mvc.perform(get(path).session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private int statusOf(String path) throws Exception {
        return mvc.perform(get(path).session(session)).andReturn().getResponse().getStatus();
    }

    private void enter() throws Exception {
        mvc.perform(post("/admin-mode/enter").session(session)).andExpect(status().is3xxRedirection());
    }

    private AuditAction lastPersonalEvent() {
        return auditRepo.findForPerson(DEV_ADMIN, PageRequest.of(0, 1)).getContent().stream()
                .map(AuditEvent::getAction).findFirst().orElse(null);
    }

    /** Signed in, an admin sees what their memberships give them - and the switch. */
    @Test
    void anAdminStartsInTheUserView() throws Exception {
        String home = page("/");
        assertThat(home).doesNotContain("href=\"/users\"").doesNotContain(">Admin mode<")
                .contains("action=\"/admin-mode/enter\"").contains("Switch to admin mode");
        assertThat(statusOf("/users")).isEqualTo(404);
        assertThat(statusOf("/activity/all")).isEqualTo(404);
        assertThat(statusOf("/employers/" + elsewhere.getId())).isEqualTo(404);
        assertThat(page("/employers")).contains("Acme AG").doesNotContain("Elsewhere Ltd");
    }

    /** Switching up brings every admin power at once, says so, and is recorded. */
    @Test
    void enteringAdminModeGivesTheAdminView() throws Exception {
        enter();

        String home = page("/");
        assertThat(home).contains("href=\"/users\"").contains(">Admin mode<")
                .contains("action=\"/admin-mode/leave\"");
        assertThat(statusOf("/users")).isEqualTo(200);
        assertThat(statusOf("/activity/all")).isEqualTo(200);
        assertThat(statusOf("/employers/" + elsewhere.getId())).isEqualTo(200);
        assertThat(lastPersonalEvent()).isEqualTo(AuditAction.ADMIN_MODE_ENTERED);
    }

    @Test
    void leavingAdminModeReturnsToTheUserView() throws Exception {
        enter();
        mvc.perform(post("/admin-mode/leave").session(session)).andExpect(status().is3xxRedirection());

        assertThat(statusOf("/users")).isEqualTo(404);
        assertThat(page("/")).doesNotContain(">Admin mode<");
        assertThat(lastPersonalEvent()).isEqualTo(AuditAction.ADMIN_MODE_LEFT);
    }

    /** Viewing as a user is an admin power like any other (spec 2.9). */
    @Test
    void viewingAsAUserNeedsAdminMode() throws Exception {
        mvc.perform(post("/users/" + MARA + "/impersonate").session(session)).andExpect(status().isNotFound());
        enter();
        mvc.perform(post("/users/" + MARA + "/impersonate").session(session))
                .andExpect(status().is3xxRedirection());
    }

    /**
     * Admin mode counts only while the stored role is admin: a demotion by the
     * admin rules (spec 2.2) ends it on the next request, and a non-admin is
     * never offered the switch nor let through it.
     */
    @Test
    void onlyAnAdminHasAdminModeAndADemotionEndsIt() throws Exception {
        enter();
        assertThat(statusOf("/users")).isEqualTo(200);

        UserEntity dev = userRepo.findById(DEV_ADMIN).orElseThrow();
        dev.setRole(UserEntity.Role.USER);
        userRepo.saveAndFlush(dev);

        assertThat(statusOf("/users")).isEqualTo(404);
        assertThat(page("/")).doesNotContain("/admin-mode/");
        mvc.perform(post("/admin-mode/enter").session(session)).andExpect(status().isNotFound());
    }
}
