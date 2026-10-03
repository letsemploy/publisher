package org.letsemploy.ojobpub_publisher.web.view;

import java.util.Locale;

/**
 * A person's picture, or their initials when there is none (spec 7.27):
 * rendered by {@code fragments/avatar}.
 */
public record Avatar(
        /** The picture's address in the application, or null. */
        String url,
        /** One or two letters, shown when there is no picture. */
        String initials) {

    public static Avatar of(String url, String name) {
        return new Avatar(url, initials(name));
    }

    /**
     * The first letter of the first and last word: "Ada Lovelace" is AL, an
     * address is its first letter, and a name in a script without spaces is
     * its first character.
     */
    static String initials(String name) {
        if (name == null || name.isBlank()) {
            return "?";
        }
        String n = name.strip();
        int at = n.indexOf('@');
        if (at > 0) {
            n = n.substring(0, at);
        }
        String[] words = n.split("\\s+");
        String first = firstLetter(words[0]);
        String last = words.length > 1 ? firstLetter(words[words.length - 1]) : "";
        return (first + last).toUpperCase(Locale.ROOT);
    }

    private static String firstLetter(String word) {
        return word.isEmpty() ? "" : new String(Character.toChars(word.codePointAt(0)));
    }
}
