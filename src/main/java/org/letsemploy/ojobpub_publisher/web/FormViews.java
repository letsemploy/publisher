package org.letsemploy.ojobpub_publisher.web;

import io.github.wimdeblauwe.htmx.spring.boot.mvc.HtmxRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Where a create or edit form renders (spec 7.4, 7.7). Opened from a button it
 * is an htmx request, not boosted, and answers the template's {@code modal}
 * fragment into {@code #modal-container}; asked for directly - a bookmark, a
 * reload, a link to a field - it is the whole page. Both come from the one
 * template, so the two cannot drift.
 *
 * <p>Saving follows the same split. From the modal, success answers
 * {@code HX-Redirect} (the library's {@code redirect:htmx:}, a
 * {@code RedirectView}, so flash messages survive) and a refusal re-renders the
 * modal with its errors; from the page, both are what they always were.
 */
public final class FormViews {

    private FormViews() {
    }

    /** {@code template} as a page, or its {@code modal} fragment when the modal asked. */
    public static String view(String template) {
        return inModal() ? template + " :: modal" : template;
    }

    /** A redirect to {@code path} that also leaves the modal for a whole page. */
    public static String redirect(String path) {
        return (inModal() ? "redirect:htmx:" : "redirect:") + path;
    }

    private static boolean inModal() {
        if (!(RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes)) {
            return false;
        }
        HtmxRequest htmx = HtmxRequest.fromRequest(attributes.getRequest());
        return htmx.isHtmxRequest() && !htmx.isBoosted() && !htmx.isHistoryRestoreRequest();
    }
}
