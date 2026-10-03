package org.letsemploy.ojobpub_publisher.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.invitation.InvitationService.InviteOutcome;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * The invitation mail (spec 2.6): sent to the invitee when an invitation is
 * created and mail is configured - here a mocked {@link JavaMailSender} - in the
 * inviter's language, and never for an address that names no account.
 *
 * <p>Not {@code @Transactional}: mail goes out after commit, which a test
 * transaction would never reach. The invitee is a fresh account, deleted
 * afterwards with its invitation.
 */
@SpringBootTest
@ActiveProfiles(resolver = TestProfiles.class)
// The mail health check wants a real JavaMailSenderImpl, not the mock below.
@TestPropertySource(properties = "management.health.mail.enabled=false")
class InvitationMailTest {

    /** From the seed: Acme, its owner the dev admin, its editor, and Edith - already invited to it. */
    private static final UUID ACME = UUID.fromString("003d6aec-021b-11f1-aefa-f649a5d91690");
    private static final UUID DEV = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final Actor OWNER = Actor.user(DEV, "dev@localhost", "dev@localhost", false,
            Map.of(ACME, MembershipRole.OWNER));

    @Autowired
    private InvitationService invitations;
    @Autowired
    private UserRepo users;
    @MockitoBean
    private JavaMailSender mailSender;

    private UserEntity invitee;

    @BeforeEach
    void someoneToInvite() {
        UserEntity user = new UserEntity();
        user.setIssuer("test");
        user.setSubject("invitee-" + UUID.randomUUID());
        user.setEmail("invitee-" + UUID.randomUUID() + "@example.com");
        user.setDisplayName("Ivy Invitee");
        invitee = users.save(user);
        LocaleContextHolder.setLocale(Locale.ENGLISH);
    }

    @AfterEach
    void cleanUp() {
        // The invitation cascades with its invitee.
        users.deleteById(invitee.getId());
        LocaleContextHolder.resetLocaleContext();
    }

    private List<SimpleMailMessage> mails() {
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, atLeast(0)).send(captor.capture());
        return captor.getAllValues();
    }

    @Test
    void theInviteeIsMailedWhoInvitedThemToWhatAndWhere() {
        assertThat(invitations.invite(ACME, invitee.getEmail(), MembershipRole.OWNER, OWNER))
                .isEqualTo(InviteOutcome.SENT);

        assertThat(mails()).singleElement().satisfies(mail -> {
            assertThat(mail.getTo()).containsExactly(invitee.getEmail());
            assertThat(mail.getSubject()).isEqualTo("You have been invited to an employer");
            assertThat(mail.getText())
                    .contains("Hello Ivy Invitee,")
                    .contains("dev@localhost invited you to join Acme")
                    .contains("as Owner.")
                    .contains("/invitations");
        });
    }

    /** Accounts store no language; the inviter's is the best guess (spec 2.6). */
    @Test
    void inTheInvitersLanguage() {
        LocaleContextHolder.setLocale(Locale.GERMAN);
        invitations.invite(ACME, invitee.getEmail(), MembershipRole.EDITOR, OWNER);

        assertThat(mails()).singleElement().satisfies(mail -> {
            assertThat(mail.getSubject()).isEqualTo("Sie wurden zu einem Arbeitgeber eingeladen");
            assertThat(mail.getText()).contains("hat Sie eingeladen");
        });
    }

    /** Once the invitee has chosen a language, the mail is in theirs (spec 7.26). */
    @Test
    void inTheInviteesLanguageOnceTheyChoseOne() {
        invitee.setLanguage("de");
        invitee = users.save(invitee);
        invitations.invite(ACME, invitee.getEmail(), MembershipRole.EDITOR, OWNER);

        assertThat(mails()).singleElement().satisfies(mail ->
                assertThat(mail.getSubject()).isEqualTo("Sie wurden zu einem Arbeitgeber eingeladen"));
    }

    /** Turned off in their settings: the invitation is made all the same, and not mailed. */
    @Test
    void anInviteeWhoTurnedItOffIsNotMailed() {
        invitee.setMailInvitations(false);
        invitee = users.save(invitee);
        assertThat(invitations.invite(ACME, invitee.getEmail(), MembershipRole.EDITOR, OWNER))
                .isEqualTo(InviteOutcome.SENT);

        assertThat(mails()).isEmpty();
        assertThat(invitations.pendingForEmployer(ACME))
                .anySatisfy(i -> assertThat(i.getInvitee().getId()).isEqualTo(invitee.getId()));
    }

    /** No account, no invitation, no mail: the form cannot be used to mail anyone at all. */
    @Test
    void anUnknownAddressIsMailedNothing() {
        assertThat(invitations.invite(ACME, "nobody-" + UUID.randomUUID() + "@example.com",
                MembershipRole.EDITOR, OWNER)).isEqualTo(InviteOutcome.SENT);
        assertThat(mails()).isEmpty();
    }

    @Test
    void aMemberOrSomeoneAlreadyInvitedIsNotMailedAgain() {
        assertThat(invitations.invite(ACME, "member@example.com", MembershipRole.EDITOR, OWNER))
                .isEqualTo(InviteOutcome.ALREADY_MEMBER);
        assertThat(invitations.invite(ACME, "editor@example.com", MembershipRole.EDITOR, OWNER))
                .isEqualTo(InviteOutcome.ALREADY_INVITED);
        assertThat(mails()).isEmpty();

        invitations.invite(ACME, invitee.getEmail(), MembershipRole.EDITOR, OWNER);
        clearInvocations(mailSender);
        assertThat(invitations.invite(ACME, invitee.getEmail(), MembershipRole.EDITOR, OWNER))
                .isEqualTo(InviteOutcome.ALREADY_INVITED);
        assertThat(mails()).isEmpty();
    }
}
