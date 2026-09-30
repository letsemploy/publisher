package org.letsemploy.ojobpub_publisher.web.view;

import java.util.List;

public record DashboardView(
        int published,
        int draft,
        int expired,
        int inactive,
        List<FeedRow> feeds,
        /** Permalinks with their URLs, the ones a website is configured with (spec 7.10). */
        List<PermalinkRow> permalinks) {

    /** Feeds currently omitting a job at serving time warrant a warning card (spec 7.10). */
    public List<FeedRow> getUnhealthyFeeds() {
        return feeds.stream().filter(f -> f.excludedCount() > 0).toList();
    }
}
