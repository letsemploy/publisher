package org.letsemploy.ojobpub_publisher.web.view;

import java.util.Map;

/** One domain - a permalink - on the Domains screen, with its quick switch (spec 7.23). */
public record PermalinkRow(
        String id,
        String name,
        String description,
        /** The feed it publishes, or null for none. */
        String feedId,
        String feedName,
        String publicUrl,
        /** The employer's website, or null for none (spec 7.23). */
        String websiteHost,
        /** The last check's result as its name, or null when never checked. */
        String checkResult,
        boolean checkWorks,
        /** When it was last checked, as shown; null when never. */
        String checkedAt,
        /** The employer's feeds, id to name, for the switch. */
        Map<String, String> feedOptions) {
}
