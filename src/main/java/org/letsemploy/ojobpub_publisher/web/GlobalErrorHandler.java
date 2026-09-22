package org.letsemploy.ojobpub_publisher.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.web.view.PageMeta;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Unexpected errors render a friendly page with a correlation id; the detail goes
 * to the log. Stack traces never reach the browser (spec 8.2).
 */
@ControllerAdvice(basePackages = "org.letsemploy.ojobpub_publisher")
@Slf4j
@RequiredArgsConstructor
public class GlobalErrorHandler {

    private final UiContextFactory uiContextFactory;

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(NotFoundException e, HttpServletRequest request, Model model) {
        model.addAttribute("ui", uiContextFactory.build(request));
        model.addAttribute("page", PageMeta.of("Not found"));
        model.addAttribute("errorTitle", e.getMessage());
        return "error";
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String unexpected(Exception e, HttpServletRequest request, Model model) {
        model.addAttribute("ui", uiContextFactory.build(request));
        String correlationId = UUID.randomUUID().toString().substring(0, 8);
        log.error("Unexpected error [{}] handling {}", correlationId, request.getRequestURI(), e);
        model.addAttribute("page", PageMeta.of("Error"));
        model.addAttribute("correlationId", correlationId);
        return "error";
    }
}
