package org.letsemploy.ojobpub_publisher.common;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Random;

@Slf4j
public abstract class BaseController {

    @ModelAttribute("currentUrl")
    public String getCurrentUrl(HttpServletRequest request) {
        return request.getRequestURI();
    }

    @ModelAttribute
    public void addAttributes(
            @RequestParam(name = "tab", required = false, defaultValue = "1") Integer tab,
            Model model
    ) {
        model.addAttribute("tab", tab);

        int min = 1;
        int max = 7;
        int rn = new Random().nextInt(max - min + 1) + min;
        model.addAttribute("randomNumber", rn);
    }
}
