package org.letsemploy.ojobpub_publisher.job;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.EntityManagerFactory;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.web.Views;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * The job list must scale (spec 10): its query count stays flat as the page
 * grows, and pagination happens in SQL rather than in memory.
 *
 * <p>Two things made it otherwise. Fetch-joining `locations` and `tags` alongside
 * a Pageable made Hibernate drop the SQL LIMIT and paginate in memory
 * (HHH90003004) - loading every matching row to return twenty. And the feed count
 * was fetched one query per row.
 */
@SpringBootTest
@ActiveProfiles({"dev", "test"})
@Transactional
class JobListQueryTest {

    @Autowired
    private JobService jobService;
    @Autowired
    private Views views;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;

    @BeforeEach
    void setUp() {
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.setStatisticsEnabled(true);
    }

    /** Everything the list screen does for one page, as the controller does it. */
    private long queriesToRenderPage(int pageSize) {
        statistics.clear();
        Page<Job> page = jobService.search(null, null, null, null,
                PageRequest.of(0, pageSize, Sort.by("title")));
        Map<UUID, Long> feedCounts = jobService.feedCounts(page.getContent());
        LocalDate today = LocalDate.now();
        List<?> rows = page.getContent().stream()
                .map(j -> views.jobRow(j, feedCounts.getOrDefault(j.getId(), 0L), today))
                .toList();
        assertThat(rows).hasSize(page.getContent().size());
        return statistics.getPrepareStatementCount();
    }

    /**
     * The N+1 guard. With a query per row, doubling the page size would roughly
     * double the query count; batching keeps it flat.
     */
    @Test
    void queryCountDoesNotGrowWithPageSize() {
        long small = queriesToRenderPage(2);
        long large = queriesToRenderPage(6);

        assertThat(large).as("queries for 6 rows vs 2 rows: %d vs %d", large, small)
                .isEqualTo(small);
    }

    /** A page is a handful of queries: the page, its count, the collections, the feed counts. */
    @Test
    void aPageCostsAHandfulOfQueries() {
        assertThat(queriesToRenderPage(6)).isLessThanOrEqualTo(6);
    }

    /** Filtering and searching must not reintroduce a per-row query either. */
    @Test
    void filteredAndSearchedPagesAreAlsoBounded() {
        statistics.clear();
        Page<Job> page = jobService.search(null, "e", Publication.Presentation.PUBLISHED, null,
                PageRequest.of(0, 20, Sort.by("title")));
        Map<UUID, Long> counts = jobService.feedCounts(page.getContent());
        LocalDate today = LocalDate.now();
        page.getContent().forEach(j -> views.jobRow(j, counts.getOrDefault(j.getId(), 0L), today));

        assertThat(statistics.getPrepareStatementCount()).isLessThanOrEqualTo(6);
    }

    /** The page really is a page: asking for one row returns one row. */
    @Test
    void paginationActuallyLimits() {
        Page<Job> first = jobService.search(null, null, null, null,
                PageRequest.of(0, 1, Sort.by("title")));
        assertThat(first.getContent()).hasSize(1);
        assertThat(first.getTotalElements()).isGreaterThan(1);
    }
}
