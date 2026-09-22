package org.letsemploy.ojobpub_publisher.api;

import static org.letsemploy.ojobpub_publisher.api.JobApiController.uuid;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.letsemploy.ojobpub_publisher.api.ApiTypes.*;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.invitation.InvitationService;
import org.letsemploy.ojobpub_publisher.membership.Membership;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.membership.MembershipService;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.token.TokenScope;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

/**
 * Membership and invitations over the management API (spec 11.2).
 *
 * <p>Consent still governs: {@code inviteMember} creates an invitation and
 * nothing more. There is no API path that makes someone a member - accepting is
 * the only write site for memberships, and only the invitee can do it (spec 2.6).
 */
@Controller
@RequiredArgsConstructor
public class PeopleApiController {

    private final MembershipService membershipService;
    private final InvitationService invitationService;
    private final ApiMapper mapper;
    private final ApiActor api;
    private final ApiErrors errors;

    @QueryMapping
    @Transactional(readOnly = true)
    public List<MemberDto> members() {
        Actor actor = api.requireScope(TokenScope.PEOPLE_READ);
        UUID employerId = api.employerId();
        membershipService.requireOwner(actor, employerId);
        return membershipService.membersOf(employerId).stream().map(mapper::member).toList();
    }

    @QueryMapping
    @Transactional(readOnly = true)
    public List<InvitationDto> pendingInvitations() {
        Actor actor = api.requireScope(TokenScope.PEOPLE_READ);
        UUID employerId = api.employerId();
        membershipService.requireOwner(actor, employerId);
        return invitationService.pendingForEmployer(employerId).stream()
                .map(mapper::invitation).toList();
    }

    /**
     * Returns an outcome, never the invitation: an address that matches no
     * account answers {@code SENT} exactly as a real invitation does, so the API
     * cannot be used to enumerate who has an account (spec 2.6).
     */
    @MutationMapping
    public InvitationPayload inviteMember(@Argument String email, @Argument String role) {
        Actor actor = api.requireScope(TokenScope.PEOPLE_WRITE);
        try {
            return InvitationPayload.ok(invitationService
                    .invite(api.employerId(), email, MembershipRole.valueOf(role), actor)
                    .name());
        } catch (ValidationFailure e) {
            return new InvitationPayload(null, errors.from(e));
        }
    }

    @MutationMapping
    public DeletePayload revokeInvitation(@Argument String id) {
        Actor actor = api.requireScope(TokenScope.PEOPLE_WRITE);
        UUID invitationId = uuid(id, "invitation");
        invitationService.revoke(invitationId, actor);
        return DeletePayload.ok(invitationId);
    }

    @MutationMapping
    public MemberPayload changeMemberRole(@Argument String userId, @Argument String role) {
        Actor actor = api.requireScope(TokenScope.PEOPLE_WRITE);
        UUID employerId = api.employerId();
        UUID member = uuid(userId, "user");
        try {
            membershipService.changeRole(employerId, member, MembershipRole.valueOf(role), actor);
        } catch (ValidationFailure e) {
            return new MemberPayload(null, errors.from(e));
        }
        return MemberPayload.ok(mapper.readMember(employerId, member));
    }

    @MutationMapping
    public DeletePayload removeMember(@Argument String userId) {
        Actor actor = api.requireScope(TokenScope.PEOPLE_WRITE);
        UUID member = uuid(userId, "user");
        try {
            membershipService.remove(api.employerId(), member, actor);
        } catch (ValidationFailure e) {
            return new DeletePayload(null, errors.from(e));
        }
        return DeletePayload.ok(member);
    }
}
