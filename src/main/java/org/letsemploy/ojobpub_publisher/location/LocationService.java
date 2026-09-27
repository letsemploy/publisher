package org.letsemploy.ojobpub_publisher.location;

import com.neovisionaries.i18n.CountryCode;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * An employer's locations (spec 3.2). Each belongs to exactly one employer: its
 * members may manage it, and nobody else sees it. A request for another
 * employer's location is answered as if it did not exist (spec 2.4).
 */
@Service
@RequiredArgsConstructor
public class LocationService {

    private final LocationRepo locationRepo;

    /** The list screen: every location of the employers in scope. */
    public List<Location> visibleTo(Collection<UUID> employerIds) {
        return employerIds.isEmpty() ? List.of() : locationRepo.findByEmployerIdInOrderByCityAsc(employerIds);
    }

    /** One employer's locations, for its headquarters select. */
    public List<Location> ofEmployer(UUID employerId) {
        return locationRepo.findByEmployerIdOrderByCityAsc(employerId);
    }

    /** The job form's picker: this employer's locations only (spec 3.3, 7.8). */
    public List<Location> search(UUID employerId, String query) {
        if (query == null || query.isBlank()) {
            return locationRepo.findTop20ByEmployerIdOrderByCityAsc(employerId);
        }
        return locationRepo.findTop20ByEmployerIdAndCityContainingIgnoreCaseOrderByCityAsc(employerId, query.trim());
    }

    /** For a member of its employer, or an admin; anyone else is told it does not exist. */
    public Location findVisible(UUID id, Actor actor) {
        Location location = locationRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Location not found: " + id));
        if (!actor.isAdmin() && !actor.getEmployerIds().contains(location.getEmployer().getId())) {
            throw new NotFoundException("Location not found: " + id);
        }
        return location;
    }

    /**
     * One of this employer's locations - for a job or a headquarters, whose owner
     * decides which locations may be attached (spec 3.3). Another employer's id
     * is refused exactly like one that does not exist, so it reveals nothing.
     */
    public Location requireOwn(UUID id, Employer employer, String field) {
        return locationRepo.findById(id)
                .filter(l -> l.getEmployer().getId().equals(employer.getId()))
                .orElseThrow(() -> new ValidationFailure(field, "Choose one of this employer's locations."));
    }

    public long usageCount(UUID id) {
        return locationRepo.countUsages(id);
    }

    @Transactional
    public Location create(Employer employer, String city, String countryCode) {
        Location location = new Location();
        location.setEmployer(employer);
        return store(location, city, countryCode);
    }

    @Transactional
    public Location update(UUID id, String city, String countryCode, Actor actor) {
        return store(findVisible(id, actor), city, countryCode);
    }

    /**
     * The existing location with this city and country, or a new one - both within
     * this employer. How a headquarters entered on the employer form joins the
     * employer's locations without becoming a duplicate.
     */
    @Transactional
    public Location findOrCreate(Employer employer, String city, String countryCode) {
        CountryCode country = country(countryCode);
        String trimmed = city(city);
        return locationRepo.findFirstByEmployerIdAndCityIgnoreCaseAndCountry(employer.getId(), trimmed, country)
                .orElseGet(() -> create(employer, trimmed, countryCode));
    }

    /** A location still referenced cannot be deleted; the UI names what uses it (spec 7.14). */
    @Transactional
    public void delete(UUID id, Actor actor) {
        Location location = findVisible(id, actor);
        if (usageCount(id) > 0) {
            throw new ValidationFailure("id", "This location is still in use.");
        }
        locationRepo.delete(location);
    }

    private Location store(Location location, String city, String countryCode) {
        String trimmed = city(city);
        CountryCode country = country(countryCode);
        // Unique within the employer (spec 3.2): said on the form, not left to the
        // database to refuse with an error nobody can read.
        locationRepo.findFirstByEmployerIdAndCityIgnoreCaseAndCountry(
                        location.getEmployer().getId(), trimmed, country)
                .filter(other -> !other.getId().equals(location.getId()))
                .ifPresent(other -> {
                    throw new ValidationFailure("city", "This employer already has this location.");
                });
        location.setCity(trimmed);
        location.setCountry(country);
        return locationRepo.save(location);
    }

    private static String city(String city) {
        if (city == null || city.isBlank()) {
            throw new ValidationFailure("city", "A city is required.");
        }
        return city.trim();
    }

    private static CountryCode country(String countryCode) {
        CountryCode country = CountryCode.getByCodeIgnoreCase(countryCode);
        if (country == null || country == CountryCode.UNDEFINED) {
            throw new ValidationFailure("country", "Select a country.");
        }
        return country;
    }
}
