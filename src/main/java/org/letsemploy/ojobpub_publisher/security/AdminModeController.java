package org.letsemploy.ojobpub_publisher.security;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/**
 * The user menu's admin-mode switch (spec 2.10, 7.3). Both land on the dashboard
 * rather than back: the page the admin was on may not exist in the other mode.
 */
@Controller
@RequiredArgsConstructor
public class AdminModeController {

    private final AdminModeService adminModeService;

    @PostMapping("/admin-mode/enter")
    public String enter(RedirectAttributes flash) {
        adminModeService.enter();
        flash.addFlashAttribute("successMsg", "adminMode.entered");
        return "redirect:/";
    }

    @PostMapping("/admin-mode/leave")
    public String leave(RedirectAttributes flash) {
        adminModeService.leave();
        flash.addFlashAttribute("successMsg", "adminMode.left");
        return "redirect:/";
    }
}
