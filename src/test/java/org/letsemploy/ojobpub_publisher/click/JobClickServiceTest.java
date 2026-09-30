package org.letsemploy.ojobpub_publisher.click;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/** What the dashboard shows of the clicks (spec 7.10), against the seeded counters. */
@SpringBootTest
@ActiveProfiles(resolver = TestProfiles.class)
class JobClickServiceTest {

    private static final UUID ACME = UUID.fromString("003d6aec-021b-11f1-aefa-f649a5d91690");
    private static final UUID PUBLISHED = UUID.fromString("8f14e45f-ceea-467a-9b4f-3a1b2c3d4e5f");
    private static final UUID INTERNSHIP = UUID.fromString("9a25f56a-dffb-478b-ac50-4b2c3d4e5f60");

    @Autowired
    private JobClickService service;

    @Test
    void theMostClickedJobComesFirstAndCountriesAreSummed() {
        JobClickService.Statistics stats = service.statistics(List.of(ACME));

        assertThat(stats.days()).isEqualTo(30);
        assertThat(stats.topJobs()).extracting(c -> c.job().getId()).startsWith(PUBLISHED, INTERNSHIP);
        assertThat(stats.topJobs().getFirst().clicks()).isGreaterThanOrEqualTo(21);
        assertThat(stats.topJobs().getFirst().job().getEmployer().getName()).isEqualTo("Acme AG");
        // Swiss clicks on every job, summed into one row, first.
        assertThat(stats.countries().getFirst().country()).isEqualTo("CH");
        assertThat(stats.countries()).extracting(JobClickService.CountryClicks::country)
                .contains("DE", "AT", JobClick.UNKNOWN_COUNTRY).doesNotHaveDuplicates();
        assertThat(stats.total()).isEqualTo(stats.countries().stream()
                .mapToLong(JobClickService.CountryClicks::clicks).sum());
    }

    /** Only the employers asked about: the caller passes its scope (spec 2.4). */
    @Test
    void anotherEmployersClicksNeverAppear() {
        JobClickService.Statistics stats = service.statistics(List.of(UUID.randomUUID()));
        assertThat(stats.topJobs()).isEmpty();
        assertThat(stats.countries()).isEmpty();
        assertThat(service.statistics(List.of()).total()).isZero();
    }

    @Test
    void machinesAreRecognisedByTheirUserAgent() {
        assertThat(service.counts("Mozilla/5.0 (Macintosh; Intel Mac OS X 14_5) Safari/605.1.15")).isTrue();
        assertThat(service.counts("facebookexternalhit/1.1")).isFalse();
        assertThat(service.counts("WhatsApp/2.23.20.0")).isFalse();
        assertThat(service.counts("curl/8.5.0")).isFalse();
        assertThat(service.counts("")).isFalse();
        assertThat(service.counts(null)).isFalse();
    }
}
