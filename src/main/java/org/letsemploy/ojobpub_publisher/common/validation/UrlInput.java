package org.letsemploy.ojobpub_publisher.common.validation;

import jakarta.validation.constraints.Pattern;

/** An optional link that is published, so only absolute http(s): never javascript: (spec 3.1). */
public record UrlInput(@Pattern(regexp = "(?s)https?://.*", message = "{validation.url.absolute}") String url) {
}
