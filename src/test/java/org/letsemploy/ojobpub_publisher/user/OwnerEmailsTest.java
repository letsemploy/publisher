package org.letsemploy.ojobpub_publisher.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.audit.AuditAction;
import org.letsemploy.ojobpub_publisher.audit.AuditRepo;
import org.letsemploy.ojobpub_publisher.membership.Membership;
import org.letsemploy.ojobpub_publisher.membership.MembershipRepo;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owner addresses (spec 7.20): every active owner's address, once each, for admins
 * only, and recorded in the admin's own log (spec 3.12). Sessions start in admin
 * mode here, as the seeded admin.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.class)
@Transactional
class OwnerEmailsTest {

    /** Mara Member, the seed's editor of Acme. */
    private static final UUID MARA_MEMBERSHIP = UUID.fromString("66666666-6666-4666-8666-666666666666");
    private static final UUID DEV_ADMIN = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private MembershipRepo membershipRepo;
    @Autowired
    private AuditRepo auditRepo;

    private String page(MockHttpSession session) throws Exception {
        return mvc.perform(get("/users/owners").session(session)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String emails(String html) {
        int start = html.indexOf("id=\"owner-emails\"");
        return html.substring(html.indexOf('>', start) + 1, html.indexOf("</textarea>", start));
    }

    @Test
    void anAdminSeesEveryActiveOwnerOnceAndItIsRecorded() throws Exception {
        String html = page(new MockHttpSession());

        // The seed's owner; its editor is no owner.
        assertThat(emails(html)).contains("dev@localhost").doesNotContain("member@example.com");
        assertThat(html).contains("BCC", "data-action=\"copy\"").containsPattern(">\\d+ address(es)?<");
        // Reached from the Users screen.
        assertThat(mvc.perform(get("/users")).andReturn().getResponse().getContentAsString())
                .contains("href=\"/users/owners\"");
        assertThat(auditRepo.findAll()).anySatisfy(e -> {
            assertThat(e.getAction()).isEqualTo(AuditAction.OWNER_EMAILS_VIEWED);
            assertThat(e.getSubjectUserId()).isEqualTo(DEV_ADMIN);
        });
    }

    /** An owner counts while active: a suspended membership leaves the list (spec 2.7). */
    @Test
    void onlyActiveOwnersAreListed() throws Exception {
        Membership mara = membershipRepo.findById(MARA_MEMBERSHIP).orElseThrow();
        mara.setRole(MembershipRole.OWNER);
        membershipRepo.saveAndFlush(mara);
        assertThat(emails(page(new MockHttpSession()))).contains("member@example.com");

        mara.setSuspendedAt(Instant.now());
        membershipRepo.saveAndFlush(mara);
        assertThat(emails(page(new MockHttpSession()))).doesNotContain("member@example.com");
    }

    /** Nobody else learns the page exists, an admin outside admin mode included (spec 2.4, 2.10). */
    @Test
    void outsideAdminModeItDoesNotExist() throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(post("/admin-mode/leave").session(session)).andExpect(status().is3xxRedirection());

        mvc.perform(get("/users/owners").session(session)).andExpect(status().isNotFound());
        assertThat(auditRepo.findAll()).noneMatch(e -> e.getAction() == AuditAction.OWNER_EMAILS_VIEWED);
    }
}
