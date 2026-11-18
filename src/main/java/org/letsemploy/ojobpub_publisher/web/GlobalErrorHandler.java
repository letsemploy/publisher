package org.letsemploy.ojobpub_publisher.web;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.web.view.PageMeta;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Unexpected errors render a friendly page with a correlation id; the detail goes
 * to the log. Stack traces never reach the browser (spec 8.2).
 */
@ControllerAdvice(basePackages = "org.letsemploy.ojobpub_publisher")
public class GlobalErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalErrorHandler.class);

    private final UiContextFactory uiContextFactory;

    public GlobalErrorHandler(UiContextFactory uiContextFactory) {
        this.uiContextFactory = uiContextFactory;
    }

    @ExceptionHandler(NotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String notFound(NotFoundException e, HttpServletRequest request, HttpServletResponse response,
                           Model model) {
        wholePage(request, response);
        model.addAttribute("ui", uiContextFactory.build(request));
        model.addAttribute("page", PageMeta.of("Not found"));
        model.addAttribute("errorTitle", e.getMessage());
        return "error";
    }

    /**
     * A malformed id or value in the URL or a form ({@code /jobs/foo}, {@code role=foo})
     * names nothing that exists: 404, like an unknown one, and no stack trace for a
     * bot trying URLs. The message is not shown, since it describes our parsing.
     */
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, IllegalArgumentException.class})
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public String malformed(Exception e, HttpServletRequest request, HttpServletResponse response, Model model) {
        log.debug("Malformed input handling {}: {}", request.getRequestURI(), e.getMessage());
        return notFound(new NotFoundException("Not found"), request, response, model);
    }

    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public String unexpected(Exception e, HttpServletRequest request, HttpServletResponse response, Model model) {
        wholePage(request, response);
        model.addAttribute("ui", uiContextFactory.build(request));
        String correlationId = UUID.randomUUID().toString().substring(0, 8);
        log.error("Unexpected error [{}] handling {}", correlationId, request.getRequestURI(), e);
        model.addAttribute("page", PageMeta.of("Error"));
        model.addAttribute("correlationId", correlationId);
        return "error";
    }

    /**
     * htmx 4 swaps an error answer like any other, into whatever the request
     * targeted. The error page is a whole page, so it replaces the body: a failed
     * modal or table refresh shows the error instead of nesting a page in a region.
     */
    private static void wholePage(HttpServletRequest request, HttpServletResponse response) {
        if (request.getHeader("HX-Request") != null) {
            response.setHeader("HX-Retarget", "body");
            response.setHeader("HX-Reswap", "innerHTML");
        }
    }
}
