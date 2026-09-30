package org.letsemploy.ojobpub_publisher.web;

import jakarta.servlet.http.HttpServletRequest;
import org.letsemploy.ojobpub_publisher.web.view.UiContext;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerMapping;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

/** Supplies the shell context to every back-office screen (spec 7.3). */
@ControllerAdvice(basePackages = "org.letsemploy.ojobpub_publisher")
public class UiContextAdvice {

    private final UiContextFactory uiContextFactory;

    public UiContextAdvice(UiContextFactory uiContextFactory) {
        this.uiContextFactory = uiContextFactory;
    }

    @ModelAttribute("ui")
    public UiContext ui(HttpServletRequest request) {
        // A response body is no page, so it has no shell. Building one resolves the
        // user and opens a session - which the public feed must never do (spec 5.1).
        if (writesABody(request)) {
            return null;
        }
        return uiContextFactory.build(request);
    }

    private static boolean writesABody(HttpServletRequest request) {
        return request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) instanceof HandlerMethod handler
                && (handler.hasMethodAnnotation(ResponseBody.class)
                || AnnotatedElementUtils.hasAnnotation(handler.getBeanType(), ResponseBody.class));
    }
}
