package org.letsemploy.ojobpub_publisher.tag;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.letsemploy.ojobpub_publisher.audit.AuditAction;
import org.letsemploy.ojobpub_publisher.audit.AuditEvent;
import org.letsemploy.ojobpub_publisher.audit.AuditLog;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * An employer's tags (spec 3.4). Each belongs to exactly one employer and is
 * unique by name within it; its members may manage it, and nobody else sees it.
 * Renaming or deleting one therefore touches only that employer's jobs.
 */
@Service
@RequiredArgsConstructor
public class TagService {

    private final TagRepo tagRepo;
    private final AuditLog auditLog;

    /** The list screen and the job form's picker: the first 20 matching, by name. */
    public List<Tag> search(Collection<UUID> employerIds, String query) {
        if (employerIds.isEmpty()) {
            return List.of();
        }
        if (query == null || query.isBlank()) {
            return tagRepo.findTop20ByEmployerIdInOrderByNameAsc(employerIds);
        }
        return tagRepo.findTop20ByEmployerIdInAndNameContainingIgnoreCaseOrderByNameAsc(
                employerIds, Tag.normalize(query));
    }

    /** All of one employer's tags, by name - uncapped, for the API's listing. */
    public List<Tag> ofEmployer(UUID employerId) {
        return tagRepo.findByEmployerIdOrderByNameAsc(employerId);
    }

    /** For a member of its employer, or an admin; anyone else is told it does not exist. */
    public Tag findVisible(Long id, Actor actor) {
        Tag tag = tagRepo.findById(id).orElseThrow(() -> new NotFoundException("Tag not found: " + id));
        if (!actor.isAdmin() && !actor.getEmployerIds().contains(tag.getEmployer().getId())) {
            throw new NotFoundException("Tag not found: " + id);
        }
        return tag;
    }

    /**
     * One of this employer's tags, for one of its jobs (spec 3.3). Another
     * employer's id is refused exactly like one that does not exist.
     */
    public Tag requireOwn(Long id, Employer employer, String field) {
        return tagRepo.findById(id)
                .filter(t -> t.getEmployer().getId().equals(employer.getId()))
                .orElseThrow(() -> new ValidationFailure(field, "Choose one of this employer's tags."));
    }

    public long jobCount(Long id) {
        return tagRepo.countJobs(id);
    }

    @Transactional
    public Tag create(Employer employer, String rawName, Actor actor) {
        Tag tag = new Tag();
        tag.setEmployer(employer);
        Tag saved = store(tag, rawName);
        auditLog.record(AuditEvent.of(AuditAction.TAG_CREATED, actor).in(employer)
                .target(saved.getId(), saved.getName()));
        return saved;
    }

    @Transactional
    public Tag update(Long id, String rawName, Actor actor) {
        Tag tag = findVisible(id, actor);
        String before = tag.getName();
        Tag saved = store(tag, rawName);
        if (!saved.getName().equals(before)) {
            auditLog.record(AuditEvent.of(AuditAction.TAG_UPDATED, actor).in(saved.getEmployer())
                    .target(saved.getId(), saved.getName()).detail(before + " → " + saved.getName()));
        }
        return saved;
    }

    /** Creating a tag this employer already has returns the existing one (spec 3.4). */
    @Transactional
    public Tag findOrCreate(Employer employer, String rawName, Actor actor) {
        String name = Tag.normalize(rawName);
        return tagRepo.findFirstByEmployerIdAndNameIgnoreCase(employer.getId(), name)
                .orElseGet(() -> create(employer, name, actor));
    }

    /** Removes it from this employer's jobs, and only theirs (spec 3.4). */
    @Transactional
    public void delete(Long id, Actor actor) {
        Tag tag = findVisible(id, actor);
        tagRepo.delete(tag);
        auditLog.record(AuditEvent.of(AuditAction.TAG_DELETED, actor).in(tag.getEmployer())
                .target(tag.getId(), tag.getName()));
    }

    private Tag store(Tag tag, String rawName) {
        String name = Tag.normalize(rawName);
        if (name == null || name.isBlank()) {
            throw new ValidationFailure("name", "A name is required.");
        }
        if (name.length() > Tag.MAX_LENGTH) {
            throw new ValidationFailure("name",
                    "At most " + Tag.MAX_LENGTH + " characters; the published schema caps tags there.");
        }
        tagRepo.findFirstByEmployerIdAndNameIgnoreCase(tag.getEmployer().getId(), name)
                .filter(other -> !other.getId().equals(tag.getId()))
                .ifPresent(other -> {
                    throw new ValidationFailure("name", "This employer already has a tag with this name.");
                });
        tag.setName(name);
        return tagRepo.save(tag);
    }
}
