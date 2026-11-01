package org.letsemploy.ojobpub_publisher.web.view;

/**
 * The Welcome page (spec 7.30): which of its links this person may follow, and
 * where each step's chapter of the manual is, in the language shown.
 *
 * @param hasEmployer   whether an employer is active; without one, steps 2-4 point at step 1
 * @param administers   whether the person administers the active employer, and so has API tokens
 * @param settings      whether Settings is offered, which it is not while viewing as someone (spec 7.26)
 */
public record WelcomeView(boolean hasEmployer, boolean administers, boolean settings,
                          String employerHelp, String jobsHelp, String feedsHelp, String peopleHelp) {
}
