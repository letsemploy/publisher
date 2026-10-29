package org.letsemploy.ojobpub_publisher.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * A page resolves who is asking once (spec 10). The controller, the scope and
 * the shell each ask, and each used to cost the user and their memberships
 * again, and the employer list twice: 16 statements for the job list where 9
 * do. {@code CurrentUserService} keeps a GET's actor for the request, and
 * {@code Scope} the employers that actor sees.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.class)
@TestPropertySource(properties =
        "spring.jpa.properties.hibernate.session_factory.statement_inspector="
                + "org.letsemploy.ojobpub_publisher.web.RecordingStatementInspector")
class RequestQueryCountTest {

    @Autowired
    private MockMvc mvc;

    /** The statements of one request, after a first that applies the session's preferences. */
    private List<String> statementsOf(String path) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mvc.perform(get(path).session(session)).andExpect(status().isOk());
        RecordingStatementInspector.STATEMENTS.clear();
        mvc.perform(get(path).session(session)).andExpect(status().isOk());
        return List.copyOf(RecordingStatementInspector.STATEMENTS);
    }

    private static long matching(List<String> statements, String... fragments) {
        return statements.stream()
                .map(String::toLowerCase)
                .filter(sql -> Arrays.stream(fragments).allMatch(sql::contains))
                .count();
    }

    @ParameterizedTest
    @ValueSource(strings = {"/", "/jobs", "/feeds", "/tags", "/people"})
    void aPageResolvesItsActorOnce(String path) throws Exception {
        List<String> statements = statementsOf(path);

        assertThat(matching(statements, "from users", "issuer=", "subject="))
                .as("%s: the user, by issuer and subject", path).isEqualTo(1);
        assertThat(matching(statements, "from memberships", "user_id=", "suspended_at is null"))
                .as("%s: the actor's memberships", path).isEqualTo(1);
        assertThat(matching(statements, "from employers", "order by"))
                .as("%s: the employers the actor sees", path).isEqualTo(1);
    }
}
