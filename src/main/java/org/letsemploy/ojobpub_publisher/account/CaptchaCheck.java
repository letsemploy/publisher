package org.letsemploy.ojobpub_publisher.account;

import com.mosersystems.captcha.CaptchaProvider;
import com.mosersystems.captcha.CaptchaVerificationException;
import com.mosersystems.captcha.CaptchaVerifier;
import com.mosersystems.captcha.mcaptcha.MCaptchaVerifier;
import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.MessageSource;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

/**
 * The captcha of the forms that mail an address a stranger typed - sign-up,
 * resending its link, a forgotten password (spec 2.12) - through whichever
 * provider {@code captcha.*} configures: hCaptcha or mCaptcha.
 *
 * <p>Without one the forms work and a warning says so at startup (spec 9.5), so
 * development needs no captcha service. The widget, and the content security
 * policy those pages need for it (spec 7.2), follow from the same verifier.
 */
@Component
public class CaptchaCheck {

    private static final Logger log = LoggerFactory.getLogger(CaptchaCheck.class);

    private final CaptchaVerifier verifier;
    private final LocalAccountProperties localAccounts;
    private final MessageSource messages;

    public CaptchaCheck(ObjectProvider<CaptchaVerifier> verifier, LocalAccountProperties localAccounts,
                        MessageSource messages) {
        this.verifier = verifier.getIfAvailable();
        this.localAccounts = localAccounts;
        this.messages = messages;
    }

    @EventListener(ContextRefreshedEvent.class)
    void warnIfMissing() {
        if (localAccounts.enabled() && verifier == null) {
            log.warn("Local accounts are enabled without a captcha (captcha.enabled): sign-up and "
                    + "forgotten-password forms mail any address typed into them (spec 2.12)");
        }
    }

    /** Which widget the forms show: {@code hcaptcha}, {@code mcaptcha}, or null for none. */
    public String provider() {
        if (verifier == null) {
            return null;
        }
        return verifier.provider() == CaptchaProvider.MCAPTCHA ? "mcaptcha" : "hcaptcha";
    }

    public String siteKey() {
        return verifier == null ? null : verifier.siteKey();
    }

    /** mCaptcha's widget address; the page frames it. */
    public String widgetUrl() {
        return verifier instanceof MCaptchaVerifier m ? m.widgetUrl() : null;
    }

    /**
     * What the pages carrying the widget add to the content security policy
     * (spec 7.2), or empty. hCaptcha loads its script, frames and styles from its
     * own hosts; mCaptcha's script is vendored, and only its widget is framed.
     */
    public String policyAdditions() {
        if ("hcaptcha".equals(provider())) {
            String hosts = "https://hcaptcha.com https://*.hcaptcha.com";
            return "script-src 'self' " + hosts + "; frame-src " + hosts + "; style-src 'self' " + hosts
                    + "; connect-src 'self' " + hosts;
        }
        if ("mcaptcha".equals(provider())) {
            URI widget = URI.create(widgetUrl());
            return "script-src 'self'; frame-src " + widget.getScheme() + "://" + widget.getAuthority();
        }
        return "";
    }

    public boolean configured() {
        return verifier != null;
    }

    /** The form parameter the widget submits its token in, or null without a captcha. */
    public String tokenParameterName() {
        return verifier == null ? null : verifier.tokenParameterName();
    }

    /** Whether the token is a solved captcha; always true without a captcha configured. */
    boolean solved(String token, String client) {
        if (verifier == null) {
            return true;
        }
        try {
            verifier.verify(token, client);
            return true;
        } catch (CaptchaVerificationException e) {
            return false;
        }
    }

    /** Refuses a form whose captcha was not solved, as a field error beside the widget. */
    public void verify(HttpServletRequest request) {
        if (verifier == null) {
            return;
        }
        try {
            verifier.verify(request.getParameter(verifier.tokenParameterName()), request.getRemoteAddr());
        } catch (CaptchaVerificationException e) {
            throw new ValidationFailure("captcha", messages.getMessage("validation.captcha", null,
                    LocaleContextHolder.getLocale()));
        }
    }
}
