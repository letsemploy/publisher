package org.letsemploy.ojobpub_publisher.web.view;

import java.util.List;
import lombok.Value;

/** Title bar contents for a screen (spec 7.3). */
@Value
public class PageMeta {
    String title;
    String subtitle;
    List<Crumb> breadcrumb;

    public static PageMeta of(String title) {
        return new PageMeta(title, null, List.of());
    }

    public static PageMeta of(String title, String subtitle) {
        return new PageMeta(title, subtitle, List.of());
    }
}
