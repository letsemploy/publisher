package org.letsemploy.ojobpub_publisher.web.view;

import java.util.List;

/**
 * The dashboard's job-link clicks (spec 7.10): the most clicked jobs and where the
 * clicks came from, over the last {@code days}. Each row's {@code share} is its
 * clicks against the table's largest, 0-100, for the bar beside it.
 */
public record ClicksView(int days, long total, List<Job> jobs, List<Country> countries) {

    public record Job(String id, String title, String employerName, long clicks, int share) {
    }

    /** {@code name} is already localized, and says "Unknown" in words for no country (spec 7.9). */
    public record Country(String code, String name, long clicks, int share) {
    }

    public boolean isEmpty() {
        return total == 0;
    }
}
