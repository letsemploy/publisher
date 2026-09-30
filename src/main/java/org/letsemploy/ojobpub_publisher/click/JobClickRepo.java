package org.letsemploy.ojobpub_publisher.click;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JobClickRepo extends JpaRepository<JobClick, JobClick.Key> {

    /** One more click on an existing counter; answers 0 when there is none yet. */
    @Modifying
    @Query("UPDATE JobClick c SET c.clicks = c.clicks + 1 WHERE c.id = :key")
    int increment(@Param("key") JobClick.Key key);

    /** Job id and clicks, most clicked first; the page bounds how many (spec 7.10). */
    @Query("SELECT c.id.jobId, SUM(c.clicks) FROM JobClick c"
            + " WHERE c.employerId IN :employerIds AND c.id.day >= :since"
            + " GROUP BY c.id.jobId ORDER BY SUM(c.clicks) DESC, c.id.jobId")
    List<Object[]> topJobs(@Param("employerIds") Collection<UUID> employerIds,
                           @Param("since") LocalDate since, Pageable page);

    /** Country and clicks, most clicks first. */
    @Query("SELECT c.id.country, SUM(c.clicks) FROM JobClick c"
            + " WHERE c.employerId IN :employerIds AND c.id.day >= :since"
            + " GROUP BY c.id.country ORDER BY SUM(c.clicks) DESC, c.id.country")
    List<Object[]> byCountry(@Param("employerIds") Collection<UUID> employerIds,
                             @Param("since") LocalDate since);
}
