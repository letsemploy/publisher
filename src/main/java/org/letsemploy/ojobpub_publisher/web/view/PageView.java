package org.letsemploy.ojobpub_publisher.web.view;

import java.util.ArrayList;
import java.util.List;
import lombok.Value;

/**
 * A slice of a list plus the links needed to page it. The URI builder keeps
 * every list state bookmarkable, which spec 7.4 requires of any control that
 * changes what the user is looking at.
 */
@Value
public class PageView<T> {
    List<T> content;
    int number;
    int size;
    long totalElements;
    String baseUri;

    public int getTotalPages() {
        return size == 0 ? 0 : (int) Math.ceil((double) totalElements / size);
    }

    public int getNumberOfElements() {
        return content.size();
    }

    public String uriForPage(int page) {
        int target = Math.max(0, Math.min(page, Math.max(0, getTotalPages() - 1)));
        String sep = baseUri.contains("?") ? "&" : "?";
        return baseUri + sep + "page=" + target;
    }

    /** A short window of page numbers around the current one. */
    public List<Integer> pageNumbers() {
        List<Integer> out = new ArrayList<>();
        int total = getTotalPages();
        int from = Math.max(0, number - 2);
        int to = Math.min(total - 1, from + 4);
        from = Math.max(0, to - 4);
        for (int i = from; i <= to; i++) {
            out.add(i);
        }
        return out;
    }
}
