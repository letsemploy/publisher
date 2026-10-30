package org.letsemploy.ojobpub_publisher.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Sign-in once the failure throttles are full of running windows (spec 2.12):
 * an attempt they cannot count fails closed. With a captcha configured it must
 * carry a solved one; without, it is refused - even with the right password,
 * because the throttle cannot tell it from the flood. Nothing is evicted to make
 * room, so a flood cannot reset the count of the address it is guessing at.
 *
 * <p>Plain unit test: the provider with a mocked repository and captcha, and
 * throttles capped at a few keys.
 */
class FullThrottleSignInTest {

    private static final String EMAIL = "owner@example.com";
    private static final String PASSWORD = "correct horse battery staple";
    private static final String CLIENT = "198.51.100.77";
    private static final int CAP = 5;

    private final PasswordEncoder encoder = PasswordEncoderFactories.createDelegatingPasswordEncoder();
    private final LocalAccountRepo accounts = mock(LocalAccountRepo.class);
    private final CaptchaCheck captcha = mock(CaptchaCheck.class);
    private LoginAttempts attempts;

    @BeforeEach
    void setUp() {
        LocalAccount account = new LocalAccount();
        account.setId(UUID.randomUUID());
        account.setEmail(EMAIL);
        account.setDisplayName("Owner");
        account.setPasswordHash(encoder.encode(PASSWORD));
        when(accounts.findByEmailIgnoreCase(EMAIL)).thenReturn(Optional.of(account));

        attempts = new LoginAttempts(new LocalAccountProperties(true, 10, 3, 10, Duration.ofMinutes(15),
                new LocalAccountProperties.Password(12, 128, 0, 0)), CAP);
        // The flood: as many distinct addresses, from as many clients, as there is room for.
        for (int i = 0; i < CAP; i++) {
            attempts.failed("junk-" + i + "@example.com", "203.0.113." + i);
        }
    }

    private LocalAuthenticationProvider provider() {
        return new LocalAuthenticationProvider(accounts, encoder, attempts, captcha);
    }

    private static UsernamePasswordAuthenticationToken signIn(String password, String captchaToken) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(CLIENT);
        if (captchaToken != null) {
            request.addParameter("captcha", captchaToken);
        }
        UsernamePasswordAuthenticationToken token = UsernamePasswordAuthenticationToken.unauthenticated(EMAIL, password);
        token.setDetails(new LoginDetails(request, "captcha"));
        return token;
    }

    @Test
    void anAttemptTheFullThrottleCannotCountIsUncounted() {
        assertThat(attempts.uncounted(EMAIL, CLIENT)).isTrue();
        assertThat(attempts.captchaRequired(EMAIL, CLIENT)).isTrue();
        assertThat(attempts.captchaRequired(CLIENT)).as("the sign-in page shows the captcha").isTrue();
    }

    /** Without a captcha, refused - the same answer as any other failure, so no oracle. */
    @Test
    void withoutACaptchaItIsRefusedEvenWithTheRightPassword() {
        when(captcha.configured()).thenReturn(false);

        assertThatThrownBy(() -> provider().authenticate(signIn(PASSWORD, null)))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void withACaptchaItMustCarryASolvedOne() {
        when(captcha.configured()).thenReturn(true);
        when(captcha.solved(any(), any())).thenReturn(false);
        when(captcha.solved(eq("solved"), any())).thenReturn(true);

        assertThatThrownBy(() -> provider().authenticate(signIn(PASSWORD, null)))
                .isInstanceOf(CaptchaRequiredException.class);
        assertThat(provider().authenticate(signIn(PASSWORD, "solved")).isAuthenticated()).isTrue();
    }

    /** Before the flood, the same sign-in needs neither. */
    @Test
    void withRoomTheSameSignInNeedsNoCaptcha() {
        LoginAttempts roomy = new LoginAttempts(new LocalAccountProperties(true, 10, 3, 10, Duration.ofMinutes(15),
                new LocalAccountProperties.Password(12, 128, 0, 0)));
        when(captcha.configured()).thenReturn(false);

        assertThat(roomy.uncounted(EMAIL, CLIENT)).isFalse();
        assertThat(new LocalAuthenticationProvider(accounts, encoder, roomy, captcha)
                .authenticate(signIn(PASSWORD, null)).isAuthenticated()).isTrue();
    }
}
