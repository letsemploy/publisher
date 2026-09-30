package org.letsemploy.ojobpub_publisher.web.view;

import java.util.List;

public record DashboardView(
        int published,
        int draft,
        int expired,
        int inactive,
        List<FeedRow> feeds,
        /** Permalinks with their URLs, the ones a website is configured with (spec 7.10). */
        List<PermalinkRow> permalinks,
        /** Clicks on the job links in the published documents (spec 5.6, 7.10). */
        ClicksView clicks) {
}
