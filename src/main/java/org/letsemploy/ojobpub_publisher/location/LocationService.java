package org.letsemploy.ojobpub_publisher.location;

import com.neovisionaries.i18n.CountryCode;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.letsemploy.ojobpub_publisher.audit.AuditAction;
import org.letsemploy.ojobpub_publisher.audit.AuditEvent;
import org.letsemploy.ojobpub_publisher.audit.AuditLog;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.common.validation.InputValidator;
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
public class LocationService {

    private final LocationRepo locationRepo;
    private final AuditLog auditLog;
    private final InputValidator inputs;

    public LocationService(LocationRepo locationRepo, AuditLog auditLog, InputValidator inputs) {
        this.locationRepo = locationRepo;
        this.auditLog = auditLog;
        this.inputs = inputs;
    }

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
    public Location create(Employer employer, String city, String countryCode, Actor actor) {
        Location location = new Location();
        location.setEmployer(employer);
        Location saved = store(location, city, countryCode);
        auditLog.record(AuditEvent.of(AuditAction.LOCATION_CREATED, actor).in(employer)
                .target(saved.getId(), saved.getLabel()));
        return saved;
    }

    @Transactional
    public Location update(UUID id, String city, String countryCode, Actor actor) {
        Location location = findVisible(id, actor);
        String before = location.getLabel();
        Location saved = store(location, city, countryCode);
        if (!saved.getLabel().equals(before)) {
            auditLog.record(AuditEvent.of(AuditAction.LOCATION_UPDATED, actor).in(saved.getEmployer())
                    .target(saved.getId(), saved.getLabel()).detail(before + " → " + saved.getLabel()));
        }
        return saved;
    }

    /**
     * The existing location with this city and country, or a new one - both within
     * this employer. How a headquarters entered on the employer form joins the
     * employer's locations without becoming a duplicate.
     */
    @Transactional
    public Location findOrCreate(Employer employer, String city, String countryCode, Actor actor) {
        inputs.check(new LocationInput(city, countryCode));
        CountryCode country = CountryCode.getByCodeIgnoreCase(countryCode);
        String trimmed = city.trim();
        return locationRepo.findFirstByEmployerIdAndCityIgnoreCaseAndCountry(employer.getId(), trimmed, country)
                .orElseGet(() -> create(employer, trimmed, countryCode, actor));
    }

    /** A location still referenced cannot be deleted; the UI names what uses it (spec 7.14). */
    @Transactional
    public void delete(UUID id, Actor actor) {
        Location location = findVisible(id, actor);
        if (usageCount(id) > 0) {
            throw new ValidationFailure("id", "This location is still in use.");
        }
        locationRepo.delete(location);
        auditLog.record(AuditEvent.of(AuditAction.LOCATION_DELETED, actor).in(location.getEmployer())
                .target(location.getId(), location.getLabel()));
    }

    private Location store(Location location, String city, String countryCode) {
        inputs.check(new LocationInput(city, countryCode));
        String trimmed = city.trim();
        CountryCode country = CountryCode.getByCodeIgnoreCase(countryCode);
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
}
