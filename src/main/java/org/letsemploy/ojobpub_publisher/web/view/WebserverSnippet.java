package org.letsemploy.ojobpub_publisher.web.view;

/**
 * One webserver configuration for a permalink (spec 7.23).
 *
 * @param server {@code apache}, {@code nginx} or {@code caddy}
 * @param kind   {@code redirect} or {@code proxy}
 * @param config the configuration, ready to paste
 */
public record WebserverSnippet(String server, String kind, String config) {

    /** The number of lines, so the text area shows all of it. */
    public int lines() {
        return (int) config.lines().count();
    }
}
