package org.letsemploy.ojobpub_publisher.web.view;

import java.util.List;

/** The dashboard (spec 7.10), top to bottom. */
public record DashboardView(
        int published,
        int draft,
        int expired,
        int inactive,
        /** Published jobs as a share of all jobs, 0-100, for the ring on the published card. */
        int publishedShare,
        /** Jobs first published within the recent days. */
        int newRecently,
        int recentDays,
        /** Clicks on the job links in the published documents (spec 5.6). */
        ClicksView clicks,
        AttentionView attention,
        /** The latest events of the employers in scope, as the Activity screen shows them (spec 7.21). */
        List<ActivityRow> activity,
        List<FeedRow> feeds,
        /** Permalinks with their URLs, the ones a website is configured with. */
        List<PermalinkRow> permalinks,
        /** Getting started: the user belongs to no employer yet (not in admin mode). */
        boolean hintEmployer,
        /** Getting started: the active employer has no job yet (not in admin mode). */
        boolean hintJob) {
}
