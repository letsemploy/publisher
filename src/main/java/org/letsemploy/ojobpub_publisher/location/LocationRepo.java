package org.letsemploy.ojobpub_publisher.location;

import com.neovisionaries.i18n.CountryCode;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface LocationRepo extends JpaRepository<Location, UUID> {

    List<Location> findAllByOrderByCityAsc();

    Optional<Location> findFirstByCityIgnoreCaseAndCountry(String city, CountryCode country);

    List<Location> findTop20ByCityContainingIgnoreCaseOrderByCityAsc(String city);

    /** How many employers and jobs still reference this location (spec 7.14). */
    @Query("""
            SELECT (SELECT COUNT(e) FROM Employer e WHERE e.headquarters.id = :id)
                 + (SELECT COUNT(j) FROM Job j JOIN j.locations l WHERE l.id = :id)
            """)
    long countUsages(@Param("id") UUID id);
}
