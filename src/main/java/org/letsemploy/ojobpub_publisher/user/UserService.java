package org.letsemploy.ojobpub_publisher.user;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.net.URI;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.membership.MembershipService;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.letsemploy.ojobpub_publisher.web.view.PageView;
import org.letsemploy.ojobpub_publisher.web.view.UserRow;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Everyone who has signed in, for platform staff (spec 7.20) - and where viewing
 * the application as one of them starts (spec 2.9). Nobody else learns the list
 * exists: a non-admin gets 404 (spec 2.4).
 */
@Service
@RequiredArgsConstructor
public class UserService {

    static final int PAGE_SIZE = 20;

    private final UserRepo userRepo;
    private final MembershipService membershipService;

    @Transactional(readOnly = true)
    public PageView<UserRow> page(String q, int page, Actor actor) {
        if (actor.isToken() || !actor.isAdmin()) {
            throw new NotFoundException("Not found.");
        }
        PageRequest request = PageRequest.of(Math.max(page, 0), PAGE_SIZE);
        Page<UserEntity> users = q == null || q.isBlank()
                ? userRepo.findAllByOrderByDisplayNameAsc(request)
                : userRepo.findByDisplayNameContainingIgnoreCaseOrEmailContainingIgnoreCaseOrderByDisplayNameAsc(
                        q.trim(), q.trim(), request);
        // One count query for the page, not one per row.
        Map<UUID, Long> employers = membershipService.countsByUser(
                users.getContent().stream().map(UserEntity::getId).toList());
        List<UserRow> rows = users.getContent().stream()
                .map(u -> new UserRow(u.getId().toString(), label(u), u.getEmail(), provider(u.getIssuer()),
                        u.getRole() == UserEntity.Role.ADMIN, employers.getOrDefault(u.getId(), 0L),
                        u.getId().equals(actor.getId()) ? "self"
                                : u.getRole() == UserEntity.Role.ADMIN ? "admin" : null))
                .toList();
        String base = q == null || q.isBlank() ? "/users" : "/users?q=" + URLEncoder.encode(q.trim(), UTF_8);
        return new PageView<>(rows, users.getNumber(), PAGE_SIZE, users.getTotalElements(), base);
    }

    private static String label(UserEntity user) {
        return user.getDisplayName() != null && !user.getDisplayName().isBlank()
                ? user.getDisplayName() : user.getEmail() != null ? user.getEmail() : user.getSubject();
    }

    /** "accounts.google.com" rather than the full issuer; "development" for the seed. */
    static String provider(String issuer) {
        try {
            String host = URI.create(issuer).getHost();
            return host != null ? host : "development";
        } catch (IllegalArgumentException notAUri) {
            return "development";
        }
    }
}
