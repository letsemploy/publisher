package org.letsemploy.ojobpub_publisher.security;

import java.util.UUID;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** View as a user from the Users screen, and stop from the banner (spec 2.9, 7.20). */
@Controller
public class ImpersonationController {

    private final ImpersonationService impersonationService;

    public ImpersonationController(ImpersonationService impersonationService) {
        this.impersonationService = impersonationService;
    }

    @PostMapping("/users/{id}/impersonate")
    public String start(@PathVariable UUID id, RedirectAttributes flash) {
        impersonationService.start(id);
        flash.addFlashAttribute("successMsg", "impersonation.started");
        return "redirect:/";
    }

    @PostMapping(ImpersonationGuard.STOP)
    public String stop(RedirectAttributes flash) {
        impersonationService.stop();
        flash.addFlashAttribute("successMsg", "impersonation.stopped");
        return "redirect:/users";
    }
}
