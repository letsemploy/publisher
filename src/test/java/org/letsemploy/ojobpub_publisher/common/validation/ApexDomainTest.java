package org.letsemploy.ojobpub_publisher.common.validation;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** The apex domain by the Public Suffix List (spec 3.13); no Spring, no database. */
class ApexDomainTest {

    private final ApexDomain.Validator validator = new ApexDomain.Validator();

    @Test
    void onlyTheRegistrableDomainItself() {
        for (String apex : new String[] {"acme.ch", "acme.co.uk", "acme.com.au", "xn--bcher-kva.ch"}) {
            assertThat(validator.isValid(apex, null)).as(apex).isTrue();
        }
        for (String sub : new String[] {"www.acme.ch", "jobs.acme.co.uk", "co.uk", "ch", "acme.example"}) {
            assertThat(validator.isValid(sub, null)).as(sub).isFalse();
        }
        // Not a domain name at all is the shape check's to report.
        assertThat(validator.isValid("10.0.0.1:80", null)).isTrue();
        assertThat(validator.isValid(null, null)).isTrue();
    }

    @Test
    void offersTheDomainOfAWebsite() {
        assertThat(ApexDomain.Validator.registrable("www.acme.co.uk")).contains("acme.co.uk");
        assertThat(ApexDomain.Validator.registrable("acme.ch")).contains("acme.ch");
        assertThat(ApexDomain.Validator.registrable("www.acme.example")).isEmpty();
        assertThat(ApexDomain.Validator.registrable(null)).isEmpty();
    }
}
