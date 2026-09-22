package org.letsemploy.ojobpub_publisher.web;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.letsemploy.ojobpub_publisher.web.view.UiContext;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Supplies the shell context to every back-office screen (spec 7.3). */
@ControllerAdvice(basePackages = "org.letsemploy.ojobpub_publisher")
@RequiredArgsConstructor
public class UiContextAdvice {

    private final UiContextFactory uiContextFactory;

    @ModelAttribute("ui")
    public UiContext ui(HttpServletRequest request) {
        return uiContextFactory.build(request);
    }
}
