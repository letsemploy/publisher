package org.letsemploy.ojobpub_publisher.ojobpub.v1;

import static org.assertj.core.api.Assertions.assertThat;

import com.neovisionaries.i18n.CountryCode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.feed.Feed;
import org.letsemploy.ojobpub_publisher.job.Job;
import org.letsemploy.ojobpub_publisher.job.JobStatus;
import org.letsemploy.ojobpub_publisher.job.JobType;
import org.letsemploy.ojobpub_publisher.location.Location;
import org.letsemploy.ojobpub_publisher.ojobpub.v1.dto.OjobpubDto;
import org.letsemploy.ojobpub_publisher.ojobpub.v1.dto.OjobpubLocationDto;
import org.letsemploy.ojobpub_publisher.ojobpub.v1.service.OjobpubService;

/**
 * The document's order depends on its content only, never on the order the
 * database returned rows in (spec 5.2), so a consumer diffing two fetches sees
 * real changes only. Each test builds the same feed twice, in opposite load
 * orders, from values that tie on every sort key but the last.
 * Plain unit test: no Spring, no database.
 */
class DocumentOrderTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 21);

    private final OjobpubService service = new OjobpubService("https://jobs.acme.example/", true);

    private static Employer employer() {
        Employer employer = new Employer();
        employer.setName("Acme AG");
        employer.setSlug("acme-ag");
        employer.setHeadquarters(new Location("Bern", CountryCode.CH));
        employer.setLastModifiedAt(Instant.parse("2026-09-01T08:00:00Z"));
        return employer;
    }

    private static Job job(Employer employer, String id, Location... locations) {
        Job job = new Job();
        job.setId(UUID.fromString(id));
        job.setEmployer(employer);
        job.setTitle("Software Engineer");
        job.setUrl("https://www.acme.example/jobs/" + id);
        job.setLanguageCode("en");
        job.setJobType(JobType.PERMANENT);
        job.setStatus(JobStatus.ACTIVE);
        job.setPublishedAt(LocalDate.of(2026, 9, 1));
        job.setLocations(new LinkedHashSet<>(List.of(locations)));
        job.setLastModifiedAt(Instant.parse("2026-09-01T08:00:00Z"));
        return job;
    }

    private OjobpubDto generate(Employer employer, Job... loaded) {
        Feed feed = new Feed();
        feed.setEmployer(employer);
        feed.setName("All jobs");
        feed.setSlug("all");
        feed.setJobs(new LinkedHashSet<>(List.of(loaded)));
        return service.generate(feed, TODAY).document();
    }

    /** Same date, same title: the id decides, whichever came back first. */
    @Test
    void jobsThatTieOnDateAndTitleKeepOneOrder() {
        Employer employer = employer();
        Location bern = new Location("Bern", CountryCode.CH);
        Job first = job(employer, "11111111-1111-4111-8111-111111111111", bern);
        Job second = job(employer, "22222222-2222-4222-8222-222222222222", bern);

        assertThat(generate(employer, second, first).jobs())
                .isEqualTo(generate(employer, first, second).jobs());
        assertThat(generate(employer, second, first).jobs().get(0).url())
                .endsWith("11111111-1111-4111-8111-111111111111");
    }

    /** A city is unique only with its country, so the country decides between two of one name. */
    @Test
    void locationsOfOneCityAreOrderedByCountry() {
        Employer employer = employer();
        Location ch = new Location("Freiburg", CountryCode.CH);
        Location de = new Location("Freiburg", CountryCode.DE);
        String id = "33333333-3333-4333-8333-333333333333";

        List<OjobpubLocationDto> one = generate(employer, job(employer, id, de, ch)).jobs().get(0).locations();
        List<OjobpubLocationDto> other = generate(employer, job(employer, id, ch, de)).jobs().get(0).locations();

        assertThat(one).isEqualTo(other);
        assertThat(one).extracting(OjobpubLocationDto::country).containsExactly("CH", "DE");
    }
}
