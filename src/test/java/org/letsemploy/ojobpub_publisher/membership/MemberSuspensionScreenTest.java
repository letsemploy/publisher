package org.letsemploy.ojobpub_publisher.membership;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Suspending a member from the People screen (spec 2.7, 7.18), as the seeded
 * admin - who is also Acme's only owner, which is what makes the "not yourself"
 * and "not the last owner" cases visible on one screen.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles({"dev", "test"})
@Transactional
class MemberSuspensionScreenTest {

    /** Fixed ids from data.sql: Acme, its owner (the dev admin), and Mara Member. */
    private static final String EMPLOYER = "003d6aec-021b-11f1-aefa-f649a5d91690";
    private static final String OWNER = "11111111-1111-4111-8111-111111111111";
    private static final String MEMBER = "44444444-4444-4444-8444-444444444444";

    private static final String PEOPLE = "/employers/" + EMPLOYER + "/people";

    @Autowired
    private MockMvc mvc;

    private String people() throws Exception {
        return mvc.perform(get(PEOPLE)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    /** Offered on a colleague's row, never on one's own (spec 2.7). */
    @Test
    void anOwnerMaySuspendOthersButIsNotOfferedThemselves() throws Exception {
        assertThat(people())
                .contains(PEOPLE + "/members/" + MEMBER + "/suspend")
                .doesNotContain(PEOPLE + "/members/" + OWNER + "/suspend");
    }

    /** Still listed, still with a role, marked in words and dated, and reversible. */
    @Test
    void aSuspendedMemberIsListedAsSuchWithAWayBack() throws Exception {
        mvc.perform(post(PEOPLE + "/members/" + MEMBER + "/suspend"))
                .andExpect(status().is3xxRedirection());

        assertThat(people())
                .contains("Mara Member")
                .contains("Suspended since")
                .contains(PEOPLE + "/members/" + MEMBER + "/reinstate")
                .doesNotContain(PEOPLE + "/members/" + MEMBER + "/suspend");

        mvc.perform(post(PEOPLE + "/members/" + MEMBER + "/reinstate"))
                .andExpect(status().is3xxRedirection());

        assertThat(people())
                .doesNotContain("Suspended since")
                .contains(PEOPLE + "/members/" + MEMBER + "/suspend");
    }

    /** Posting directly is refused by the service, and the screen says why. */
    @Test
    void suspendingOneselfIsRefusedWithAReason() throws Exception {
        mvc.perform(post(PEOPLE + "/members/" + OWNER + "/suspend"))
                .andExpect(status().is3xxRedirection())
                .andExpect(result -> assertThat(result.getFlashMap().get("errorMsg"))
                        .isEqualTo("employer.people.cannotSuspendSelf"));
    }
}
