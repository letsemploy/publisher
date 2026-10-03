package org.letsemploy.ojobpub_publisher.invitation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.invitation.InvitationService.InviteOutcome;
import org.letsemploy.ojobpub_publisher.mail.Mailer;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/**
 * Without a mail server an invitation works exactly as before (spec 2.6), and is
 * not even handed to the mailer - which would warn about every one of them. The
 * mocked {@link Mailer} answers {@code configured()} with false.
 */
@SpringBootTest
@ActiveProfiles(resolver = TestProfiles.class)
@Transactional
class InvitationWithoutMailTest {

    private static final UUID ACME = UUID.fromString("003d6aec-021b-11f1-aefa-f649a5d91690");
    private static final UUID DEV = UUID.fromString("11111111-1111-4111-8111-111111111111");

    @Autowired
    private InvitationService invitations;
    @Autowired
    private UserRepo users;
    @MockitoBean
    private Mailer mailer;

    @Test
    void anInvitationIsCreatedAndNothingIsSent() {
        UserEntity user = new UserEntity();
        user.setIssuer("test");
        user.setSubject("invitee-" + UUID.randomUUID());
        user.setEmail("invitee-" + UUID.randomUUID() + "@example.com");
        users.save(user);

        assertThat(invitations.invite(ACME, user.getEmail(), MembershipRole.EDITOR,
                Actor.user(DEV, "dev@localhost", "dev@localhost", false, Map.of(ACME, MembershipRole.OWNER))))
                .isEqualTo(InviteOutcome.SENT);
        assertThat(invitations.pendingForEmployer(ACME))
                .anySatisfy(i -> assertThat(i.getInvitee().getId()).isEqualTo(user.getId()));
        verify(mailer, never()).send(any());
    }
}
