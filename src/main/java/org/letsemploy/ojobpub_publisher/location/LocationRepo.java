package org.letsemploy.ojobpub_publisher.location;

import com.neovisionaries.i18n.CountryCode;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Every query is by employer: a location belongs to one (spec 3.2). */
public interface LocationRepo extends JpaRepository<Location, UUID> {

    /** The list screen, across the employers in scope; the employer is shown per row. */
    @EntityGraph(attributePaths = "employer")
    List<Location> findByEmployerIdInOrderByCityAsc(Collection<UUID> employerIds);

    List<Location> findByEmployerIdOrderByCityAsc(UUID employerId);

    List<Location> findTop20ByEmployerIdOrderByCityAsc(UUID employerId);

    List<Location> findTop20ByEmployerIdAndCityContainingIgnoreCaseOrderByCityAsc(UUID employerId, String city);

    Optional<Location> findFirstByEmployerIdAndCityIgnoreCaseAndCountry(UUID employerId, String city,
                                                                       CountryCode country);

    /** How many employers and jobs still reference this location (spec 7.14). */
    @Query("""
            SELECT (SELECT COUNT(e) FROM Employer e WHERE e.headquarters.id = :id)
                 + (SELECT COUNT(j) FROM Job j JOIN j.locations l WHERE l.id = :id)
            """)
    long countUsages(@Param("id") UUID id);
}
