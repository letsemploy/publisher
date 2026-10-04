package org.letsemploy.ojobpub_publisher.summary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.employer.EmployerRepo;
import org.letsemploy.ojobpub_publisher.membership.Membership;
import org.letsemploy.ojobpub_publisher.membership.MembershipRepo;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The weekly summary mail (spec 7.28) with mail configured - a mocked
 * {@link JavaMailSender}: who gets one, what it covers, and that a week's
 * summary goes out once.
 *
 * <p>Not {@code @Transactional}: mail goes out after commit, which a test
 * transaction would never reach. The reader is a fresh editor of the seed's Acme,
 * whose jobs always give something to report (an incomplete one, clicks
 * yesterday), deleted afterwards with their membership.
 */
@SpringBootTest
@ActiveProfiles(resolver = TestProfiles.class)
// The mail health check wants a real JavaMailSenderImpl, not the mock below.
@TestPropertySource(properties = "management.health.mail.enabled=false")
class WeeklySummaryMailTest {

    private static final UUID ACME = UUID.fromString("003d6aec-021b-11f1-aefa-f649a5d91690");

    @Autowired
    private SummaryService summaries;
    @Autowired
    private UserRepo users;
    @Autowired
    private MembershipRepo memberships;
    @Autowired
    private EmployerRepo employers;
    @MockitoBean
    private JavaMailSender mailSender;

    private UserEntity reader;
    private Membership membership;

    @BeforeEach
    void aSubscribedEditorOfAcme() {
        UserEntity user = new UserEntity();
        user.setIssuer("test");
        user.setSubject("summary-" + UUID.randomUUID());
        user.setEmail("summary-" + UUID.randomUUID() + "@example.com");
        user.setDisplayName("Sam Summary");
        user.setMailSummary(true);
        reader = users.save(user);
        membership = memberships.save(new Membership(reader, employers.findById(ACME).orElseThrow(),
                MembershipRole.EDITOR));
    }

    @AfterEach
    void cleanUp() {
        // The membership cascades with its user.
        users.deleteById(reader.getId());
    }

    /** What went to the reader; the seed's people never subscribe, but filter anyway. */
    private List<SimpleMailMessage> mails() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeast(0)).send(captor.capture());
        return captor.getAllValues().stream()
                .filter(m -> m.getTo() != null && List.of(m.getTo()).contains(reader.getEmail()))
                .toList();
    }

    @Test
    void aSubscribedMemberGetsTheirEmployersWeek() {
        summaries.sendDue(Instant.now());

        assertThat(mails()).singleElement().satisfies(mail -> {
            assertThat(mail.getSubject()).isEqualTo("Your weekly summary");
            assertThat(mail.getText())
                    .contains("Hello Sam Summary,")
                    .contains("== Acme AG ==")
                    .contains("Published: ")
                    .contains("Clicks: ")
                    .contains("Active but incomplete, so not published:")
                    .contains("http://localhost:8080/jobs/")
                    .contains("http://localhost:8080/settings")
                    .doesNotContain("[(")
                    .doesNotContain("??");
            // An editor is not shown the owner's API tokens.
            assertThat(mail.getText()).doesNotContain("API tokens");
        });
    }

    @Test
    void notSubscribedNoMail() {
        reader.setMailSummary(false);
        reader = users.save(reader);

        summaries.sendDue(Instant.now());

        assertThat(mails()).isEmpty();
    }

    /** Claimed once a week: a second run, or a second instance, finds nobody due. */
    @Test
    void onceAWeek() {
        Instant monday = Instant.now();
        summaries.sendDue(monday);
        assertThat(mails()).hasSize(1);
        clearInvocations(mailSender);

        summaries.sendDue(monday.plus(Duration.ofHours(1)));
        assertThat(mails()).isEmpty();

        summaries.sendDue(monday.plus(Duration.ofDays(7)));
        assertThat(mails()).hasSize(1);
    }

    @Test
    void aSuspendedAccountIsNotMailed() {
        reader.setSuspendedAt(Instant.now());
        reader = users.save(reader);

        summaries.sendDue(Instant.now());

        assertThat(mails()).isEmpty();
    }

    /** A suspended membership is no membership (spec 2.7): with no other, nothing to report. */
    @Test
    void aSuspendedMembershipLeavesItsEmployerOut() {
        membership.setSuspendedAt(Instant.now());
        memberships.save(membership);

        summaries.sendDue(Instant.now());

        assertThat(mails()).isEmpty();
    }

    /** No request to take a language from: theirs, if they chose one (spec 7.26). */
    @Test
    void inTheirLanguage() {
        reader.setLanguage("de");
        reader = users.save(reader);

        summaries.sendDue(Instant.now());

        assertThat(mails()).singleElement().satisfies(mail -> {
            assertThat(mail.getSubject()).isEqualTo("Ihre wöchentliche Zusammenfassung");
            assertThat(mail.getText()).contains("Veröffentlicht: ");
        });
    }
}
