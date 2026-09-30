package org.letsemploy.ojobpub_publisher.employer;

import com.neovisionaries.i18n.CountryCode;
import java.util.List;
import java.util.UUID;
import org.letsemploy.ojobpub_publisher.audit.AuditAction;
import org.letsemploy.ojobpub_publisher.audit.AuditEvent;
import org.letsemploy.ojobpub_publisher.audit.AuditLog;
import org.letsemploy.ojobpub_publisher.common.Slugs;
import org.letsemploy.ojobpub_publisher.common.ResourceLimits;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.location.LocationService;
import org.letsemploy.ojobpub_publisher.membership.MembershipService;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EmployerService {

    private static final Logger log = LoggerFactory.getLogger(EmployerService.class);

    private final EmployerRepo employerRepo;
    private final LocationService locationService;
    private final MembershipService membershipService;
    private final ResourceLimits limits;
    private final UserRepo userRepo;
    private final AuditLog auditLog;

    public EmployerService(EmployerRepo employerRepo,
                           LocationService locationService,
                           MembershipService membershipService,
                           ResourceLimits limits,
                           UserRepo userRepo,
                           AuditLog auditLog) {
        this.employerRepo = employerRepo;
        this.locationService = locationService;
        this.membershipService = membershipService;
        this.limits = limits;
        this.userRepo = userRepo;
        this.auditLog = auditLog;
    }

    /** Editors see only their employers; an admin sees all (spec 2.1). */
    public List<Employer> visibleTo(Actor user) {
        if (user.isAdmin()) {
            return employerRepo.findAllByOrderByNameAsc();
        }
        if (user.getEmployerIds().isEmpty()) {
            return List.of();
        }
        return employerRepo.findAllById(user.getEmployerIds()).stream()
                .sorted(java.util.Comparator.comparing(Employer::getName))
                .toList();
    }

    public Page<Employer> pageVisibleTo(Actor user, String query, Pageable pageable) {
        if (user.isAdmin()) {
            return query == null || query.isBlank()
                    ? employerRepo.findAllByOrderByNameAsc(pageable)
                    : employerRepo.findByNameContainingIgnoreCaseOrderByNameAsc(query, pageable);
        }
        if (user.getEmployerIds().isEmpty()) {
            return Page.empty(pageable);
        }
        return employerRepo.findByIdInOrderByNameAsc(user.getEmployerIds(), pageable);
    }

    /**
     * A non-member gets "not found", not "forbidden", so the existence of other
     * employers' records is not disclosed (spec 2.4).
     */
    public Employer findVisible(UUID id, Actor user) {
        Employer employer = employerRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Employer not found: " + id));
        if (!user.isAdmin() && !user.getEmployerIds().contains(id)) {
            throw new NotFoundException("Employer not found: " + id);
        }
        return employer;
    }

    /**
     * Creating an employer is open to any signed-in user and makes them its owner
     * (spec 2.7); editing it takes an owner membership or the platform admin role
     * (spec 2.1). Enforced here rather than in the controller, so no route can
     * forget it - the UI hiding a button is not authorization (spec 2.4).
     */
    @Transactional
    public Employer save(UUID id, String name, String slug, String url, String industry,
                         Headquarters headquarters, Actor actor) {
        if (name == null || name.isBlank()) {
            throw new ValidationFailure("name", "A name is required.");
        }
        if (headquarters == null || headquarters.isEmpty()) {
            throw new ValidationFailure("headquarters",
                    "A headquarters location is required; the published document must carry it.");
        }
        if (headquarters.isNew()) {
            // Checked before anything is written, under the form's own field names.
            CountryCode country = CountryCode.getByCodeIgnoreCase(headquarters.newCountry());
            if (country == null || country == CountryCode.UNDEFINED) {
                throw new ValidationFailure("hqCountry", "Select the country of the headquarters.");
            }
        } else if (id == null) {
            throw new ValidationFailure("headquarters",
                    "A new employer has no locations yet: enter the headquarters city and country.");
        }
        boolean creating = id == null;
        Employer employer;
        if (creating) {
            // Creating grants the creator a membership (spec 2.7), so the
            // membership quota decides this before any row is written. grant()
            // would refuse it anyway; refusing here means no wasted work.
            limits.requireRoomForMemberships(
                    () -> membershipService.countMembershipsOf(actor.getId()));
            employer = new Employer();
        } else {
            membershipService.requireOwner(actor, id);
            employer = employerRepo.findById(id)
                    .orElseThrow(() -> new NotFoundException("Employer not found: " + id));
        }
        employer.setName(name.trim());
        employer.setSlug(Slugs.slugify(slug == null || slug.isBlank() ? name : slug, "employer"));
        employer.setUrl(blankToNull(url));
        employer.setIndustry(blankToNull(industry));
        Employer saved = employerRepo.save(employer);
        // The headquarters is one of the employer's own locations (spec 3.1), so a
        // new employer is saved first and its location created against it - the
        // reason location_id may be null in the database, never once this returns.
        saved.setHeadquarters(headquarters.isNew()
                ? locationService.findOrCreate(saved, headquarters.newCity(), headquarters.newCountry(), actor)
                : locationService.requireOwn(headquarters.locationId(), saved, "headquarters"));
        saved = employerRepo.save(saved);

        if (creating) {
            // No employer ever exists without someone responsible for it (spec 2.7).
            UserEntity creator = userRepo.findById(actor.getId())
                    .orElseThrow(() -> new NotFoundException("User not found: " + actor.getId()));
            membershipService.createOwner(creator, saved);
        }
        auditLog.record(AuditEvent.of(creating ? AuditAction.EMPLOYER_CREATED : AuditAction.EMPLOYER_UPDATED,
                actor).in(saved).target(saved.getId(), saved.getName()));
        return saved;
    }

    public long count() {
        return employerRepo.count();
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /**
     * Delete an employer, and with it everything it holds (spec 2.7, 7.13): its
     * jobs, feeds - whose public URLs then answer 404 - locations, tags, members,
     * invitations and API tokens. The database cascades all of it.
     *
     * <p>Its owners may, and platform admins. Nobody else learns it exists: an
     * editor or a stranger gets 404 (spec 2.4). A service token never may, even
     * one holding the owner role - a credential in some CI configuration must not
     * be able to destroy the workspace it was issued for.
     *
     * <p>The name must be typed back exactly. The screen asks for it, and this is
     * where it is checked, so skipping the screen does not skip the check.
     */
    @Transactional
    public void delete(UUID id, String typedName, Actor actor) {
        if (actor.isToken()) {
            throw new NotFoundException("Employer not found: " + id);
        }
        membershipService.requireOwner(actor, id);
        Employer employer = employerRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Employer not found: " + id));
        if (typedName == null || !typedName.trim().equals(employer.getName())) {
            throw new ValidationFailure("confirmName", "Type the employer's name exactly to delete it.");
        }
        employerRepo.deleteWithEverything(id);
        // Kept after the employer is gone - the one row that says what became of
        // it - readable by admins only from here on (spec 3.12).
        auditLog.record(AuditEvent.of(AuditAction.EMPLOYER_DELETED, actor).in(employer)
                .target(id, employer.getName()));
        log.info("User {} deleted employer {} ({})", actor.getId(), id, employer.getName());
    }
}
