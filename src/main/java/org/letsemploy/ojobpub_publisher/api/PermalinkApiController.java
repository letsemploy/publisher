package org.letsemploy.ojobpub_publisher.api;

import static org.letsemploy.ojobpub_publisher.api.JobApiController.uuid;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.letsemploy.ojobpub_publisher.api.ApiTypes.*;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.employer.EmployerService;
import org.letsemploy.ojobpub_publisher.feed.Permalink;
import org.letsemploy.ojobpub_publisher.feed.PermalinkService;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.token.TokenScope;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

/**
 * Permalinks over the management API (spec 5.5, 11.3), under the feed scopes:
 * a permalink is part of how the employer's feeds are published. Mutations are
 * not transactional, as for every mutation here (see {@link ApiMapper}).
 */
@Controller
@RequiredArgsConstructor
public class PermalinkApiController {

    private final PermalinkService permalinkService;
    private final EmployerService employerService;
    private final ApiMapper mapper;
    private final ApiActor api;
    private final ApiErrors errors;

    @QueryMapping
    @Transactional(readOnly = true)
    public List<PermalinkDto> permalinks() {
        api.requireScope(TokenScope.FEEDS_READ);
        LocalDate today = LocalDate.now();
        return permalinkService.findByEmployer(api.employerId()).stream()
                .map(permalink -> mapper.permalink(permalink, today))
                .toList();
    }

    @MutationMapping
    public PermalinkPayload createPermalink(@Argument Map<String, Object> input) {
        Actor actor = api.requireScope(TokenScope.FEEDS_WRITE);
        try {
            return ok(permalinkService.save(null, employerService.findVisible(api.employerId(), actor),
                    str(input, "name"), str(input, "description"), feedId(str(input, "feedId")), actor), actor);
        } catch (ValidationFailure e) {
            return new PermalinkPayload(null, errors.from(e));
        }
    }

    @MutationMapping
    public PermalinkPayload updatePermalink(@Argument String id, @Argument Map<String, Object> input) {
        Actor actor = api.requireScope(TokenScope.FEEDS_WRITE);
        // Resolved against the actor first: the id alone must not reach the save.
        Permalink permalink = permalinkService.findVisible(uuid(id, "permalink"), actor);
        try {
            return ok(permalinkService.save(permalink.getId(), permalink.getEmployer(), str(input, "name"),
                    str(input, "description"), feedId(str(input, "feedId")), actor), actor);
        } catch (ValidationFailure e) {
            return new PermalinkPayload(null, errors.from(e));
        }
    }

    @MutationMapping
    public PermalinkPayload setPermalinkFeed(@Argument String id, @Argument String feedId) {
        Actor actor = api.requireScope(TokenScope.FEEDS_WRITE);
        try {
            return ok(permalinkService.retarget(uuid(id, "permalink"), feedId(feedId), actor), actor);
        } catch (ValidationFailure e) {
            return new PermalinkPayload(null, errors.from(e));
        }
    }

    @MutationMapping
    public DeletePayload deletePermalink(@Argument String id) {
        Actor actor = api.requireScope(TokenScope.FEEDS_WRITE);
        UUID permalinkId = uuid(id, "permalink");
        permalinkService.delete(permalinkId, actor);
        return DeletePayload.ok(permalinkId);
    }

    private PermalinkPayload ok(Permalink permalink, Actor actor) {
        return PermalinkPayload.ok(mapper.readPermalink(permalink.getId(), actor));
    }

    /**
     * Null for none. A malformed id is refused like another employer's feed - as
     * data in userErrors, not a fault - since both are "not one of your feeds".
     */
    private static UUID feedId(String feedId) {
        if (feedId == null || feedId.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(feedId);
        } catch (IllegalArgumentException e) {
            throw new ValidationFailure("feed", "Choose one of this employer's feeds.");
        }
    }

    private static String str(Map<String, Object> input, String key) {
        Object value = input.get(key);
        return value == null ? null : value.toString();
    }
}
