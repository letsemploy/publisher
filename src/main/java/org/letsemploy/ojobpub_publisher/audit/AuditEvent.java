package org.letsemploy.ojobpub_publisher.audit;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.UserEntity;

/**
 * One thing that happened: who did what, to what, in which employer, and about
 * whom (spec 3.12). Written once and never changed.
 *
 * <p>Built fluently by the service that made the change and handed to
 * {@link AuditLog#record}:
 * {@code AuditEvent.of(TAG_CREATED, actor).in(employer).target(tag.getId(), tag.getName())}.
 *
 * <p>Ids are plain columns rather than associations: the log is read far more
 * often than it joins, and a row must stay readable when what it names is gone,
 * which is what the labels are for.
 */
@Entity
@Table(name = "audit_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuditEvent {

    /** Who acted. SYSTEM is the application itself, such as the admin rules at sign-in. */
    public enum ActorType {
        USER, TOKEN, SYSTEM
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @UuidGenerator(style = UuidGenerator.Style.TIME)
    private UUID id;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false)
    private ActorType actorType;

    @Column(name = "actor_user_id")
    private UUID actorUserId;

    @Column(name = "actor_token_id")
    private UUID actorTokenId;

    @Column(name = "actor_label", nullable = false)
    private String actorLabel;

    /** No foreign key, like every id here: the row outlives the employer (spec 3.12). */
    @Column(name = "employer_id")
    private UUID employerId;

    @Column(name = "employer_label")
    private String employerLabel;

    /** The person this is about, whose own log it appears in. */
    @Column(name = "subject_user_id")
    private UUID subjectUserId;

    @Column(name = "target_id")
    private String targetId;

    @Column(name = "target_label")
    private String targetLabel;

    @Column
    private String detail;

    /** An event by this actor - or by the application itself when the actor is null. */
    public static AuditEvent of(AuditAction action, Actor actor) {
        AuditEvent event = new AuditEvent();
        event.action = action;
        event.occurredAt = Instant.now();
        if (actor == null || actor.isAnonymous()) {
            event.actorType = ActorType.SYSTEM;
            event.actorLabel = "system";
        } else if (actor.isToken()) {
            event.actorType = ActorType.TOKEN;
            event.actorTokenId = actor.getId();
            event.actorLabel = actor.getDisplayName();
        } else {
            event.actorType = ActorType.USER;
            event.actorUserId = actor.getId();
            event.actorLabel = actor.getDisplayName();
        }
        return event;
    }

    public AuditEvent in(Employer employer) {
        this.employerId = employer.getId();
        this.employerLabel = employer.getName();
        return this;
    }

    public AuditEvent about(UserEntity subject) {
        this.subjectUserId = subject.getId();
        return this;
    }

    public AuditEvent target(Object id, String label) {
        this.targetId = id == null ? null : id.toString();
        this.targetLabel = label;
        return this;
    }

    public AuditEvent detail(String detail) {
        this.detail = detail;
        return this;
    }
}
