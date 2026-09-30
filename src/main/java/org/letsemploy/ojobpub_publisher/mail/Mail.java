package org.letsemploy.ojobpub_publisher.mail;

import java.util.Locale;
import java.util.Map;

/**
 * A message to send once the change that caused it has committed (spec 2.12).
 *
 * @param template  a plain-text template under {@code templates/mail/}, without suffix
 * @param subjectKey the message key of the subject line
 * @param variables what the template reads; links are absolute
 * @param locale    the language of the person who asked for it
 */
public record Mail(String to, String template, String subjectKey, Map<String, Object> variables, Locale locale) {
}
