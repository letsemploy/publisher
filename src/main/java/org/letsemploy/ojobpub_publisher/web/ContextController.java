package org.letsemploy.ojobpub_publisher.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Switching the active employer and the theme (spec 7.3). Both are ordinary form
 * posts followed by a redirect, so they work with JavaScript disabled.
 */
@Controller
@RequiredArgsConstructor
public class ContextController {

    private final EmployerContext employerContext;

    @PostMapping("/context/employer")
    public String switchEmployer(@RequestParam(required = false) String employerId,
                                 HttpServletRequest request) {
        employerContext.setActiveEmployerId(
                employerId == null || employerId.isBlank() ? null : UUID.fromString(employerId));
        return redirectBack(request);
    }

    @PostMapping("/context/theme")
    public String switchTheme(@RequestParam String theme, HttpServletRequest request) {
        employerContext.setTheme("dark".equals(theme) ? "dark" : "light");
        return redirectBack(request);
    }

    /** Return the user to the screen they were on, never to a fixed landing page. */
    private String redirectBack(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        if (referer != null && !referer.isBlank()) {
            int schemeEnd = referer.indexOf("://");
            int pathStart = schemeEnd < 0 ? -1 : referer.indexOf('/', schemeEnd + 3);
            if (pathStart > 0) {
                return "redirect:" + referer.substring(pathStart);
            }
        }
        return "redirect:/";
    }
}
