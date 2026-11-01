package org.letsemploy.ojobpub_publisher.web.view;

import java.util.List;
import org.letsemploy.ojobpub_publisher.manual.ManualTopic;

/**
 * Title bar contents for a screen (spec 7.3), and the section of the user manual
 * its help icon opens (spec 7.29), or null for none.
 */
public record PageMeta(String title, String subtitle, List<Crumb> breadcrumb, ManualTopic help) {

    public PageMeta(String title, String subtitle, List<Crumb> breadcrumb) {
        this(title, subtitle, breadcrumb, null);
    }

    public static PageMeta of(String title) {
        return new PageMeta(title, null, List.of());
    }

    public static PageMeta of(String title, String subtitle) {
        return new PageMeta(title, subtitle, List.of());
    }

    /** The same title bar, with a help icon opening {@code topic}. */
    public PageMeta help(ManualTopic topic) {
        return new PageMeta(title, subtitle, breadcrumb, topic);
    }
}
