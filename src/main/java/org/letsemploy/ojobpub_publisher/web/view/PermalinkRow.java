package org.letsemploy.ojobpub_publisher.web.view;

import java.util.Map;

/** One permalink on the Feeds screen, with its quick switch (spec 7.23). */
public record PermalinkRow(
        String id,
        String name,
        String description,
        /** The feed it publishes, or null for none. */
        String feedId,
        String feedName,
        String publicUrl,
        /** The employer's feeds, id to name, for the switch. */
        Map<String, String> feedOptions) {
}
