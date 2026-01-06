package org.letsemploy.ojobpub_publisher.employer;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.letsemploy.ojobpub_publisher.common.Slugs;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.location.LocationService;
import org.letsemploy.ojobpub_publisher.security.AppUser;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmployerService {

    private final EmployerRepo employerRepo;
    private final LocationService locationService;

    /** Editors see only their employers; an admin sees all (spec 2.1). */
    public List<Employer> visibleTo(AppUser user) {
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

    public Page<Employer> pageVisibleTo(AppUser user, String query, Pageable pageable) {
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
    public Employer findVisible(UUID id, AppUser user) {
        Employer employer = employerRepo.findById(id)
                .orElseThrow(() -> new NotFoundException("Employer not found: " + id));
        if (!user.isAdmin() && !user.getEmployerIds().contains(id)) {
            throw new NotFoundException("Employer not found: " + id);
        }
        return employer;
    }

    @Transactional
    public Employer save(UUID id, String name, String slug, String url, String industry, UUID locationId) {
        if (name == null || name.isBlank()) {
            throw new ValidationFailure("name", "A name is required.");
        }
        if (locationId == null) {
            throw new ValidationFailure("headquarters",
                    "A headquarters location is required; the published document must carry it.");
        }
        Employer employer = id == null ? new Employer()
                : employerRepo.findById(id)
                        .orElseThrow(() -> new NotFoundException("Employer not found: " + id));
        employer.setName(name.trim());
        employer.setSlug(Slugs.slugify(slug == null || slug.isBlank() ? name : slug, "employer"));
        employer.setUrl(blankToNull(url));
        employer.setIndustry(blankToNull(industry));
        employer.setHeadquarters(locationService.findById(locationId));
        return employerRepo.save(employer);
    }

    public long count() {
        return employerRepo.count();
    }

    private String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
