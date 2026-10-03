package org.letsemploy.ojobpub_publisher.web;

import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Switching the active employer (spec 2.5, 7.3): an ordinary form post followed by
 * a redirect, so it works with JavaScript disabled. Language and theme are
 * settings (spec 7.26), chosen on that page.
 */
@Controller
public class ContextController {

    private final EmployerContext employerContext;

    public ContextController(EmployerContext employerContext) {
        this.employerContext = employerContext;
    }

    @PostMapping("/context/employer")
    public String switchEmployer(@RequestParam(required = false) String employerId) {
        // A choice, not a grant: one the user may not see resolves to their first
        // employer (spec 2.5). There is no "none", so a blank choice changes nothing.
        if (employerId != null && !employerId.isBlank()) {
            employerContext.setActiveEmployerId(UUID.fromString(employerId));
        }
        // To the dashboard, not back: the screen the user was on may show a record
        // of the employer they just left, which the new scope no longer covers.
        return "redirect:/";
    }
}
