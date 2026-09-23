package org.letsemploy.ojobpub_publisher.job;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * The status filter must select what a job *presents* as (spec 7.6).
 *
 * <p>PUBLISHED, EXPIRED and INCOMPLETE are all stored as ACTIVE and told apart by
 * the publication rules, so the filter evaluates those rules in SQL. That makes
 * two expressions of one rule - {@link Publication#presentation} in Java and
 * {@link Publication#JPQL_PRESENTATION_FILTER} in JPQL - and two copies drift.
 * These tests assert they agree for every job in the database.
 */
@SpringBootTest
@ActiveProfiles(resolver = TestProfiles.class)
@Transactional  // the Java rule reads job.locations, which is lazy; keep a session open
class PublicationFilterTest {

    @Autowired
    private JobService jobService;
    @Autowired
    private JobRepo jobRepo;

    private List<Job> filteredBy(Publication.Presentation presentation) {
        return jobService.search(null, null, presentation, null,
                PageRequest.of(0, 200, Sort.by("title"))).getContent();
    }

    /** For each value, the SQL filter returns exactly the jobs the Java rule agrees with. */
    @ParameterizedTest(name = "SQL and Java agree on {0}")
    @EnumSource(Publication.Presentation.class)
    void sqlFilterMatchesTheJavaRule(Publication.Presentation presentation) {
        LocalDate today = LocalDate.now();

        List<UUID> fromSql = filteredBy(presentation).stream().map(Job::getId).sorted().toList();
        List<UUID> fromJava = jobRepo.findAll().stream()
                .filter(j -> Publication.presentation(j, today) == presentation)
                .map(Job::getId).sorted().toList();

        assertThat(fromSql).as("%s: SQL filter vs Publication.presentation", presentation)
                .isEqualTo(fromJava);
    }

    /** The bug: every value returned the same rows, because all three mapped to ACTIVE. */
    @Test
    void theDerivedValuesSelectDifferentJobs() {
        List<UUID> published = filteredBy(Publication.Presentation.PUBLISHED)
                .stream().map(Job::getId).toList();
        List<UUID> expired = filteredBy(Publication.Presentation.EXPIRED)
                .stream().map(Job::getId).toList();
        List<UUID> incomplete = filteredBy(Publication.Presentation.INCOMPLETE)
                .stream().map(Job::getId).toList();

        assertThat(published).isNotEmpty();
        assertThat(expired).isNotEmpty();
        assertThat(incomplete).isNotEmpty();
        assertThat(published).doesNotContainAnyElementsOf(expired).doesNotContainAnyElementsOf(incomplete);
        assertThat(expired).doesNotContainAnyElementsOf(incomplete);
    }

    /** Every job lands in exactly one bucket, and the buckets cover the whole table. */
    @Test
    void theFiveValuesPartitionEveryJob() {
        long total = jobRepo.count();
        long summed = 0;
        for (Publication.Presentation p : Publication.Presentation.values()) {
            summed += filteredBy(p).size();
        }
        assertThat(summed).as("the five filters partition the table").isEqualTo(total);
    }

    @Test
    void noFilterReturnsEverything() {
        assertThat(filteredBy(null)).hasSize((int) jobRepo.count());
    }

    /** A filter combined with a search term still narrows both ways. */
    @Test
    void filterCombinesWithTheSearchTerm() {
        List<Job> published = filteredBy(Publication.Presentation.PUBLISHED);
        String title = published.get(0).getTitle();

        List<Job> narrowed = jobService.search(null, title, Publication.Presentation.PUBLISHED, null,
                PageRequest.of(0, 200, Sort.by("title"))).getContent();

        assertThat(narrowed).isNotEmpty().allSatisfy(j -> assertThat(j.getTitle()).contains(title));
        assertThat(narrowed.size()).isLessThanOrEqualTo(published.size());
    }

    /** Expiry is evaluated against today, not baked into the row. */
    @Test
    void theDateWindowIsEvaluatedAtQueryTime() {
        LocalDate today = LocalDate.now();
        assertThat(filteredBy(Publication.Presentation.EXPIRED)).isNotEmpty().allSatisfy(j ->
                assertThat(Publication.withinDateWindow(j, today)).isFalse());
    }
}
