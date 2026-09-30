package org.letsemploy.ojobpub_publisher.web.view;

import java.util.List;

/**
 * The dashboard's "needs attention" card (spec 7.10): published jobs about to
 * close, active jobs that cannot be published, and forgotten drafts. Each group
 * shows its first few rows and says how many more there are.
 */
public record AttentionView(Group closing, Group incomplete, Group stale) {

    /**
     * @param more how many rows beyond {@code rows}, listed at {@code listUrl}
     */
    public record Group(List<Row> rows, int more, String listUrl) {

        public boolean isEmpty() {
            return rows.isEmpty();
        }
    }

    /** A job, its employer, and why it is here in words ("closes in 3 days"). */
    public record Row(String id, String title, String employerName, String note) {
    }

    public boolean isEmpty() {
        return closing.isEmpty() && incomplete.isEmpty() && stale.isEmpty();
    }
}
