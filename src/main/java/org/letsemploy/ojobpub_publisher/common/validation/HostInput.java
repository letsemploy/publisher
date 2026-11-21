package org.letsemploy.ojobpub_publisher.common.validation;

import jakarta.validation.constraints.Pattern;

/**
 * An optional website domain, already lower-cased and in ASCII: a name with a dot
 * and a top-level domain, so no address, no port, no path - and the registrable
 * domain itself, not a subdomain such as www (spec 3.13, 7.23).
 */
public record HostInput(
        @ApexDomain
        @Pattern(regexp = "(?=.{1,253}$)(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\\.)+(?:[a-z]{2,63}|xn--[a-z0-9-]{1,59})",
                message = "{validation.host}") String websiteHost) {
}
