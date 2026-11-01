package org.letsemploy.ojobpub_publisher.manual;

import java.util.Locale;

/**
 * Where a screen's help icon points in the user manual (spec 7.29): a page of
 * {@code docs/manual/content/{lang}/} and, for a section, the explicit heading id
 * it carries there ({@code ## Creating a job {#create}}). The ids are the same in
 * every language, so one topic serves both; {@code ManualTest} checks that each
 * one exists in each.
 */
public enum ManualTopic {

    DASHBOARD("dashboard-activity", "dashboard"),
    ACTIVITY("dashboard-activity", "activity"),
    JOBS("jobs", "shown-as"),
    JOB("jobs", "readiness"),
    JOB_FORM("jobs", "create"),
    FEEDS("feeds", "feeds"),
    PERMALINKS("feeds", "permalinks"),
    PEOPLE("people", "people"),
    INVITATIONS("getting-started", "first-employer"),
    EMPLOYERS("getting-started", "first-employer"),
    PROFILE("locations-tags", "profile"),
    LOCATIONS("locations-tags", "locations"),
    TAGS("locations-tags", "tags"),
    TOKENS("api-tokens", null),
    SETTINGS("settings", null),
    ACCOUNT("settings", "account");

    private final String page;
    private final String anchor;

    ManualTopic(String page, String anchor) {
        this.page = page;
        this.anchor = anchor;
    }

    public String page() {
        return page;
    }

    public String anchor() {
        return anchor;
    }

    /** The path of this topic in the manual's version of {@code locale}'s language. */
    public String path(Locale locale) {
        return "/docs/" + ManualHandler.language(locale) + "/" + page + ".html"
                + (anchor == null ? "" : "#" + anchor);
    }
}
