package org.letsemploy.ojobpub_publisher.account;

import jakarta.servlet.http.HttpServletRequest;
import java.io.Serial;
import org.springframework.security.web.authentication.WebAuthenticationDetails;

/** The client, and the captcha token if the sign-in form carried one (spec 2.12). */
public final class LoginDetails extends WebAuthenticationDetails {

    @Serial
    private static final long serialVersionUID = 1L;

    private final transient String captchaToken;

    public LoginDetails(HttpServletRequest request, String captchaParameter) {
        super(request);
        this.captchaToken = captchaParameter == null ? null : request.getParameter(captchaParameter);
    }

    public String getCaptchaToken() {
        return captchaToken;
    }
}
