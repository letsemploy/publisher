package org.letsemploy.ojobpub_publisher.audit;

import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.membership.MembershipService;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads the audit log, and decides who may (spec 3.12). There are two scopes,
 * and one row may be in both:
 *
 * <ul>
 *   <li>an employer's log - every member, but the owners-only actions only for
 *       those who administer it ({@link AuditAction.Audience});</li>
 *   <li>a person's own log - what was done to them and what they did, for them
 *       alone.</li>
 * </ul>
 *
 * Platform admins read everything, including the log of an employer that no
 * longer exists. Anyone else asking for what they may not read is told it does
 * not exist (spec 2.4). A service token reads nothing here.
 */
@Service
public class AuditService {

    static final int PAGE_SIZE = 50;

    private final AuditRepo auditRepo;
    private final MembershipService membershipService;

    public AuditService(AuditRepo auditRepo, MembershipService membershipService) {
        this.auditRepo = auditRepo;
        this.membershipService = membershipService;
    }

    /**
     * The logs of these employers, as the actor may read them. An employer the
     * actor does not belong to is left out rather than refused: the list comes
     * from the screen's scope (spec 2.5), not from the request.
     */
    @Transactional(readOnly = true)
    public Page<AuditEvent> forEmployers(Collection<UUID> employerIds, Actor actor, int page) {
        return forEmployers(employerIds, actor, pageable(page));
    }

    /**
     * The latest events of the employers in scope, for the dashboard (spec 7.10):
     * the same log and the same audience as {@link #forEmployers}, only fewer.
     */
    @Transactional(readOnly = true)
    public List<AuditEvent> latest(Collection<UUID> employerIds, Actor actor, int count) {
        return forEmployers(employerIds, actor, PageRequest.of(0, count)).getContent();
    }

    private Page<AuditEvent> forEmployers(Collection<UUID> employerIds, Actor actor, Pageable pageable) {
        refuseTokens(actor);
        List<UUID> administered = employerIds.stream()
                .filter(id -> membershipService.canAdminister(actor, id)).toList();
        List<UUID> member = employerIds.stream()
                .filter(id -> !administered.contains(id) && actor.getEmployerIds().contains(id)).toList();
        if (administered.isEmpty() && member.isEmpty()) {
            return Page.empty(pageable);
        }
        return auditRepo.findForEmployers(orNone(administered), orNone(member), AuditAction.ownersOnly(),
                pageable);
    }

    /** Everything, for platform staff. */
    @Transactional(readOnly = true)
    public Page<AuditEvent> everything(Actor actor, int page) {
        refuseTokens(actor);
        if (!actor.isAdmin()) {
            throw new NotFoundException("Not found.");
        }
        return auditRepo.findAllByOrderByOccurredAtDesc(pageable(page));
    }

    /** A person's own log: for them, and for platform staff. */
    @Transactional(readOnly = true)
    public Page<AuditEvent> forPerson(UUID userId, Actor actor, int page) {
        refuseTokens(actor);
        if (!actor.isAdmin() && !userId.equals(actor.getId())) {
            throw new NotFoundException("Not found.");
        }
        return auditRepo.findForPerson(userId, pageable(page));
    }

    private static void refuseTokens(Actor actor) {
        if (actor.isToken() || actor.isAnonymous()) {
            throw new NotFoundException("Not found.");
        }
    }

    private static Pageable pageable(int page) {
        return PageRequest.of(Math.max(page, 0), PAGE_SIZE);
    }

    /**
     * An empty IN list is not portable SQL, so an unused side of the query is
     * given an id that matches nothing.
     */
    private static Collection<UUID> orNone(List<UUID> ids) {
        return ids.isEmpty() ? List.of(new UUID(0, 0)) : ids;
    }
}
