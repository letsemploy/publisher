package org.letsemploy.ojobpub_publisher.account;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.letsemploy.ojobpub_publisher.audit.AuditAction;
import org.letsemploy.ojobpub_publisher.audit.AuditEvent;
import org.letsemploy.ojobpub_publisher.audit.AuditLog;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.common.validation.InputValidator;
import org.letsemploy.ojobpub_publisher.mail.Mail;
import org.letsemploy.ojobpub_publisher.mail.Mailer;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Local accounts (spec 2.12): signing up, confirming the address, and setting,
 * resetting and changing the password.
 *
 * <p><b>No oracle.</b> Signing up, resending the link and a forgotten password
 * return nothing, and do what fits the address - mail a link, mail the holder,
 * or nothing - so the screen after each is the same whoever asked (spec 2.6).
 *
 * <p><b>The password is chosen from the link.</b> Sign-up asks for an address and
 * a name only. Taking a password there would let anyone register someone else's
 * address with a password of their own, and have it go live the day its owner
 * follows the link. Chosen on the page the link opens, it is set by whoever holds
 * the mailbox.
 */
@Service
public class LocalAccountService {

    static final Duration VERIFY_LIFETIME = Duration.ofHours(24);
    static final Duration RESET_LIFETIME = Duration.ofHours(1);
    private static final int TOKEN_BYTES = 32;

    private final LocalAccountRepo accounts;
    private final AccountTokenRepo tokens;
    private final UserRepo users;
    private final PasswordEncoder passwordEncoder;
    private final InputValidator inputs;
    private final Mailer mailer;
    private final AuditLog auditLog;
    private final MessageSource messages;
    private final String baseUrl;
    private final SecureRandom random = new SecureRandom();

    public LocalAccountService(LocalAccountRepo accounts,
                               AccountTokenRepo tokens,
                               UserRepo users,
                               PasswordEncoder passwordEncoder,
                               InputValidator inputs,
                               Mailer mailer,
                               AuditLog auditLog,
                               MessageSource messages,
                               @Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.accounts = accounts;
        this.tokens = tokens;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.inputs = inputs;
        this.mailer = mailer;
        this.auditLog = auditLog;
        this.messages = messages;
        this.baseUrl = baseUrl;
    }

    /**
     * A new address gets a pending account and a link to complete it; a pending
     * one gets a fresh link and the name given now; one already in use mails its
     * holder, and nothing else changes.
     */
    @Transactional
    public void signUp(AccountForms.SignUp form, Locale locale) {
        inputs.check(form);
        String email = form.email().trim();
        LocalAccount account = accounts.findByEmailIgnoreCase(email).orElse(null);
        if (account != null && account.isVerified()) {
            mailer.send(new Mail(account.getEmail(), "signup-existing", "mail.signupExisting.subject",
                    Map.of("name", account.getDisplayName(), "loginUrl", baseUrl + "/login",
                            "forgotUrl", baseUrl + "/password/forgot"), locale));
            return;
        }
        if (account == null) {
            account = new LocalAccount();
            account.setEmail(email);
        }
        account.setDisplayName(form.name().trim());
        accounts.save(account);
        mailVerification(account, locale);
    }

    /** A new link for a pending account, and silence for anything else. */
    @Transactional
    public void resend(AccountForms.Address form, Locale locale) {
        inputs.check(form);
        accounts.findByEmailIgnoreCase(form.email().trim())
                .filter(account -> !account.isVerified())
                .ifPresent(account -> mailVerification(account, locale));
    }

    /**
     * A reset link for an account that has a password; a pending one is sent its
     * sign-up link again, which is what it is missing.
     */
    @Transactional
    public void forgot(AccountForms.Address form, Locale locale) {
        inputs.check(form);
        accounts.findByEmailIgnoreCase(form.email().trim()).ifPresent(account -> {
            if (!account.isVerified()) {
                mailVerification(account, locale);
                return;
            }
            String token = issue(account, AccountToken.Purpose.RESET_PASSWORD, RESET_LIFETIME);
            // In the holder's own language once they have chosen one (spec 7.26);
            // whoever asked may not be them, and the mail is theirs.
            Locale theirs = users.findByIssuerAndSubject(LocalAccount.ISSUER, account.getId().toString())
                    .map(UserEntity::getLanguage).map(Locale::forLanguageTag).orElse(locale);
            mailer.send(new Mail(account.getEmail(), "reset", "mail.reset.subject",
                    Map.of("name", account.getDisplayName(),
                            "link", baseUrl + "/password/reset?token=" + token), theirs));
        });
    }

    /** What a mailed link opens, if it still may be used. */
    @Transactional(readOnly = true)
    public Optional<AccountToken> usable(String token, AccountToken.Purpose purpose) {
        return find(token).filter(t -> t.getPurpose() == purpose && t.isUsable(Instant.now()));
    }

    /**
     * Sets the password from a mailed link: completing a sign-up, which also
     * confirms the address, or a reset, which ends every other session.
     *
     * @return false when the link is unknown, used or expired
     */
    @Transactional
    public boolean choosePassword(AccountForms.ChoosePassword form, AccountToken.Purpose purpose) {
        AccountToken token = usable(form.token(), purpose).orElse(null);
        if (token == null) {
            return false;
        }
        LocalAccount account = token.getAccount();
        inputs.check(new AccountForms.ChoosePassword(form.token(), form.password(), form.passwordRepeat(),
                account.getEmail()));
        Instant now = Instant.now();
        token.setUsedAt(now);
        if (!account.isVerified()) {
            account.setVerifiedAt(now);
        }
        setPassword(account, form.password(), now);
        if (purpose == AccountToken.Purpose.RESET_PASSWORD) {
            record(account, AuditAction.PASSWORD_RESET, null);
        }
        return true;
    }

    /**
     * Changes the password of the account signed in. Every other session ends;
     * the caller renews its own (spec 2.12).
     *
     * @return when the change took effect
     */
    @Transactional
    public Instant changePassword(UUID accountId, AccountForms.ChangePassword form, Actor actor) {
        LocalAccount account = accounts.findById(accountId).orElseThrow();
        inputs.check(new AccountForms.ChangePassword(form.current(), form.password(), form.passwordRepeat(),
                account.getEmail()));
        if (account.getPasswordHash() == null || !passwordEncoder.matches(form.current(), account.getPasswordHash())) {
            throw new ValidationFailure("current", messages.getMessage("validation.password.wrong", null,
                    LocaleContextHolder.getLocale()));
        }
        Instant now = Instant.now();
        setPassword(account, form.password(), now);
        record(account, AuditAction.PASSWORD_CHANGED, actor);
        return now;
    }

    /** When the account's password last changed; sessions older than that are over. */
    @Transactional(readOnly = true)
    public Optional<Instant> passwordChangedAt(UUID accountId) {
        return accounts.findById(accountId).map(LocalAccount::getPasswordChangedAt);
    }

    private void setPassword(LocalAccount account, String password, Instant now) {
        account.setPasswordHash(passwordEncoder.encode(password));
        account.setPasswordChangedAt(now);
        accounts.save(account);
        // Whatever links were still open for it are no longer needed.
        for (AccountToken.Purpose purpose : AccountToken.Purpose.values()) {
            tokens.findByAccountIdAndPurposeAndUsedAtIsNull(account.getId(), purpose)
                    .forEach(t -> t.setUsedAt(now));
        }
    }

    private void mailVerification(LocalAccount account, Locale locale) {
        String token = issue(account, AccountToken.Purpose.VERIFY_EMAIL, VERIFY_LIFETIME);
        mailer.send(new Mail(account.getEmail(), "verify", "mail.verify.subject",
                Map.of("name", account.getDisplayName(),
                        "link", baseUrl + "/register/complete?token=" + token), locale));
    }

    /** A new link, voiding the account's earlier ones of the same kind (spec 2.12). */
    private String issue(LocalAccount account, AccountToken.Purpose purpose, Duration lifetime) {
        Instant now = Instant.now();
        tokens.findByAccountIdAndPurposeAndUsedAtIsNull(account.getId(), purpose)
                .forEach(t -> t.setUsedAt(now));
        byte[] bytes = new byte[TOKEN_BYTES];
        random.nextBytes(bytes);
        String secret = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        AccountToken token = new AccountToken();
        token.setAccount(account);
        token.setPurpose(purpose);
        token.setTokenHash(sha256(secret));
        token.setExpiresAt(now.plus(lifetime));
        tokens.save(token);
        return secret;
    }

    private Optional<AccountToken> find(String secret) {
        return secret == null || secret.isBlank() ? Optional.empty() : tokens.findByTokenHash(sha256(secret));
    }

    /**
     * In the person's own log (spec 3.12) - once they have a users row, which a
     * local account gets at its first sign-in. A reset from a link has no signed-in
     * actor, so the application is named.
     */
    private void record(LocalAccount account, AuditAction action, Actor actor) {
        users.findByIssuerAndSubject(LocalAccount.ISSUER, account.getId().toString()).ifPresent(user ->
                auditLog.record(AuditEvent.of(action, actor).about(user).target(user.getId(), user.getLabel())));
    }

    static String sha256(String secret) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every JVM", e);
        }
    }
}
