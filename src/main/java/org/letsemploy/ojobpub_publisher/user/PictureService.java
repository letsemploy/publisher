package org.letsemploy.ojobpub_publisher.user;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.letsemploy.ojobpub_publisher.audit.AuditAction;
import org.letsemploy.ojobpub_publisher.audit.AuditEvent;
import org.letsemploy.ojobpub_publisher.audit.AuditLog;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.letsemploy.ojobpub_publisher.membership.MembershipService;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account pictures (spec 7.27): one's own upload, or the provider's copied in,
 * and who may see whose.
 *
 * <p>Seeing a picture takes what seeing the person takes elsewhere: being them,
 * an admin, or a member of an employer they belong to - the People screen shows
 * every member to every other (spec 7.18). Anyone else is answered 404, as for a
 * person with no picture (spec 2.4).
 */
@Service
public class PictureService {

    /** A provider's picture to fetch, or to drop when the provider no longer names one. */
    public record ProviderPictureChanged(UUID userId, String url) {
    }

    private final UserPictureRepo pictures;
    private final UserRepo users;
    private final MembershipService memberships;
    private final AuditLog auditLog;
    private final ApplicationEventPublisher events;
    private final boolean fetchProvider;

    public PictureService(UserPictureRepo pictures, UserRepo users, MembershipService memberships,
                          AuditLog auditLog, ApplicationEventPublisher events,
                          @Value("${app.pictures.fetch-provider:true}") boolean fetchProvider) {
        this.pictures = pictures;
        this.users = users;
        this.memberships = memberships;
        this.auditLog = auditLog;
        this.events = events;
        this.fetchProvider = fetchProvider;
    }

    /** The picture's address in the application, versioned so a cache never shows an old one. */
    public static String url(UUID userId, Instant updatedAt) {
        return "/pictures/" + userId + "?v=" + updatedAt.toEpochMilli();
    }

    /** One user's picture address, or empty when they have none. */
    @Transactional(readOnly = true)
    public Optional<String> urlFor(UUID userId) {
        return urlsFor(java.util.List.of(userId)).values().stream().findFirst();
    }

    /** The addresses for a whole list in one query, by user; those without one are absent. */
    @Transactional(readOnly = true)
    public Map<UUID, String> urlsFor(Collection<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return pictures.findVersions(userIds).stream()
                .collect(Collectors.toMap(UserPictureRepo.Version::getUserId,
                        v -> url(v.getUserId(), v.getUpdatedAt())));
    }

    /** Whether a user's picture is one they uploaded, rather than the provider's or none. */
    @Transactional(readOnly = true)
    public boolean hasUpload(UUID userId) {
        return pictures.findOrigin(userId)
                .map(o -> o.getSource() == UserPicture.Source.UPLOAD).orElse(false);
    }

    /** A user's picture, if the actor may see that user; otherwise 404, as for none. */
    @Transactional(readOnly = true)
    public UserPicture find(UUID userId, Actor actor) {
        if (!maySee(userId, actor)) {
            throw new NotFoundException("Not found.");
        }
        return pictures.findById(userId).orElseThrow(() -> new NotFoundException("Not found."));
    }

    private boolean maySee(UUID userId, Actor actor) {
        if (actor.isAnonymous() || actor.isToken()) {
            return false;
        }
        return actor.getId().equals(userId) || actor.isAdmin()
                || actor.getEmployerIds().stream().anyMatch(employerId -> memberships.isMember(userId, employerId));
    }

    /**
     * One's own picture, from an upload: shrunk and re-encoded by
     * {@link PictureProcessor}, and from then on never replaced by the provider's.
     */
    @Transactional
    public void upload(Actor actor, InputStream content) {
        UserEntity user = self(actor);
        byte[] processed = PictureProcessor.process(read(content));
        UserPicture picture = pictures.findById(user.getId()).orElseGet(() -> new UserPicture(user.getId()));
        picture.setContent(processed);
        picture.setContentType(PictureProcessor.CONTENT_TYPE);
        picture.setSource(UserPicture.Source.UPLOAD);
        picture.setSourceUrl(null);
        picture.setUpdatedAt(Instant.now());
        pictures.save(picture);
        auditLog.record(AuditEvent.of(AuditAction.PICTURE_CHANGED, actor).about(user)
                .target(user.getId(), user.getLabel()));
    }

    /**
     * Removes one's picture, whichever it is. The provider's comes back at the
     * next sign-in, if the provider has one: the stored address it is compared
     * with is gone with it.
     */
    @Transactional
    public void remove(Actor actor) {
        UserEntity user = self(actor);
        if (!pictures.existsById(user.getId())) {
            return;
        }
        pictures.deleteById(user.getId());
        auditLog.record(AuditEvent.of(AuditAction.PICTURE_REMOVED, actor).about(user)
                .target(user.getId(), user.getLabel()));
    }

    /**
     * What the provider says the picture is, seen at sign-in. Reads only: a
     * download is slow and may fail, so it is announced, and done after commit
     * and off the request by {@link ProviderPictureFetcher}. An upload is left
     * alone, and so is a picture already fetched from this address.
     */
    @Transactional(readOnly = true)
    public void providerPictureSeen(UUID userId, String url) {
        Optional<UserPictureRepo.Origin> origin = pictures.findOrigin(userId);
        if (origin.map(o -> o.getSource() == UserPicture.Source.UPLOAD).orElse(false)) {
            return;
        }
        String current = origin.map(UserPictureRepo.Origin::getSourceUrl).orElse(null);
        if (url == null ? origin.isEmpty() : url.equals(current)) {
            return;
        }
        if (url != null && !fetchProvider) {
            return;
        }
        events.publishEvent(new ProviderPictureChanged(userId, url));
    }

    /**
     * Stores a picture fetched from the provider - unless the person uploaded
     * their own meanwhile, or the account is gone. Not audited: like the name, it
     * is the provider's, refreshed without anyone acting (spec 2.2).
     */
    @Transactional
    public void storeProviderPicture(UUID userId, String url, byte[] processed) {
        if (!users.existsById(userId)) {
            return;
        }
        UserPicture picture = pictures.findById(userId).orElseGet(() -> new UserPicture(userId));
        if (picture.getSource() == UserPicture.Source.UPLOAD) {
            return;
        }
        picture.setContent(processed);
        picture.setContentType(PictureProcessor.CONTENT_TYPE);
        picture.setSource(UserPicture.Source.PROVIDER);
        picture.setSourceUrl(url);
        picture.setUpdatedAt(Instant.now());
        pictures.save(picture);
    }

    /** The provider no longer names a picture: its copy goes, an upload stays. */
    @Transactional
    public void dropProviderPicture(UUID userId) {
        pictures.findById(userId)
                .filter(p -> p.getSource() == UserPicture.Source.PROVIDER)
                .ifPresent(pictures::delete);
    }

    private UserEntity self(Actor actor) {
        if (actor.isToken() || actor.isAnonymous()) {
            throw new NotFoundException("Not found.");
        }
        return users.findById(actor.getId()).orElseThrow(() -> new NotFoundException("Not found."));
    }

    /** At most one byte over the limit, so a huge body is refused without being held. */
    private static byte[] read(InputStream content) {
        try (InputStream in = content) {
            return in.readNBytes(PictureProcessor.MAX_BYTES + 1);
        } catch (IOException e) {
            throw new PictureProcessor.Rejected(PictureProcessor.Reason.UNREADABLE);
        }
    }
}
