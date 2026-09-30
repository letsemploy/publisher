package org.letsemploy.ojobpub_publisher.web.view;

import java.util.List;

/**
 * The dashboard's job-link clicks (spec 7.10): the total over the last
 * {@code days} and its trend, a value per day for the sparkline and the
 * tracking strip, the most clicked jobs and where the clicks came from.
 *
 * <p>The charts are hints; every figure they draw is also here as text.
 *
 * @param trendDays the days of each job's own trend, shorter than the window
 * @param sparkline the daily clicks, oldest first, as Tabler's sparkline reads them ("3,0,5")
 * @param blocks    one per day, oldest first, for the tracking strip
 * @param summary   the strip in words, for its aria-label
 */
public record ClicksView(int days, int trendDays, long total, Trend trend, String sparkline, List<Block> blocks,
                         String summary, List<Job> jobs, List<Country> countries) {

    /**
     * Clicks now against the period before. {@code direction} is "up", "down",
     * "flat" or "new" (nothing before to compare with); {@code text} says it in
     * words, so the colour and the arrow are never the only signal (spec 7.9).
     */
    public record Trend(String direction, String text) {
    }

    /** A day of the tracking strip: its activity class ("" for none) and its tooltip. */
    public record Block(String level, String title) {
    }

    /**
     * @param share     clicks against the table's largest, 0-100, for the bar
     * @param sparkline the job's clicks per day over the trend days
     */
    public record Job(String id, String title, String employerName, long clicks, int share,
                      String sparkline, long recentTotal, Trend trend) {
    }

    /** {@code name} is already localized, and says "Unknown" in words for no country (spec 7.9). */
    public record Country(String code, String name, long clicks, int share) {
    }

    public boolean isEmpty() {
        return total == 0 && jobs.isEmpty();
    }
}
