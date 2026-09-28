package org.letsemploy.ojobpub_publisher.web.view;

import java.util.Map;
import lombok.Value;

/** One permalink on the Feeds screen, with its quick switch (spec 7.23). */
@Value
public class PermalinkRow {
    String id;
    String name;
    String description;
    String employerName;
    /** The feed it publishes, or null for none. */
    String feedId;
    String feedName;
    String publicUrl;
    /** The employer's feeds, id to name, for the switch. */
    Map<String, String> feedOptions;
}
