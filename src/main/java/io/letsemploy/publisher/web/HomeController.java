package io.letsemploy.publisher.web;

import io.letsemploy.publisher.service.ReferenceDataService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class HomeController {

    private final ReferenceDataService referenceDataService;

    public HomeController(ReferenceDataService referenceDataService) {
        this.referenceDataService = referenceDataService;
    }

    @GetMapping("/")
    public String home(Model model) {
        model.addAttribute("userCount", referenceDataService.users().count());
        model.addAttribute("tenantCount", referenceDataService.tenants().count());
        model.addAttribute("domainCount", referenceDataService.domains().count());
        model.addAttribute("jobCount", referenceDataService.jobs().count());
        model.addAttribute("exportCount", referenceDataService.exports().count());
        return "home";
    }
}
