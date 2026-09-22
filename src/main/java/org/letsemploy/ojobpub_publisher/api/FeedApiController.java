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
import org.letsemploy.ojobpub_publisher.feed.Feed;
import org.letsemploy.ojobpub_publisher.feed.FeedService;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.token.TokenScope;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

/** Feeds and their membership over the management API (spec 11.2). */
@Controller
@RequiredArgsConstructor
public class FeedApiController {

    private final FeedService feedService;
    private final EmployerService employerService;
    private final ApiMapper mapper;
    private final ApiActor api;
    private final ApiErrors errors;

    @QueryMapping
    @Transactional(readOnly = true)
    public List<FeedDto> feeds() {
        api.requireScope(TokenScope.FEEDS_READ);
        LocalDate today = LocalDate.now();
        return feedService.findByEmployer(api.employerId()).stream()
                .map(feed -> mapper.feed(feed, today))
                .toList();
    }

    @MutationMapping
    public FeedPayload createFeed(@Argument Map<String, Object> input) {
        Actor actor = api.requireScope(TokenScope.FEEDS_WRITE);
        try {
            return ok(feedService.save(null, employerService.findVisible(api.employerId(), actor),
                    str(input, "name"), str(input, "slug"), str(input, "description")), actor);
        } catch (ValidationFailure e) {
            return new FeedPayload(null, errors.from(e));
        }
    }

    @MutationMapping
    public FeedPayload updateFeed(@Argument String id, @Argument Map<String, Object> input) {
        Actor actor = api.requireScope(TokenScope.FEEDS_WRITE);
        // Resolved against the actor first: the id alone must not reach the save.
        Feed feed = feedService.findVisible(uuid(id, "feed"), actor);
        try {
            return ok(feedService.save(feed.getId(), feed.getEmployer(), str(input, "name"),
                    str(input, "slug"), str(input, "description")), actor);
        } catch (ValidationFailure e) {
            return new FeedPayload(null, errors.from(e));
        }
    }

    @MutationMapping
    public FeedPayload addJobToFeed(@Argument String feedId, @Argument String jobId) {
        Actor actor = api.requireScope(TokenScope.FEEDS_WRITE);
        try {
            return ok(feedService.addJob(uuid(feedId, "feed"), uuid(jobId, "job"), actor), actor);
        } catch (ValidationFailure e) {
            return new FeedPayload(null, errors.from(e));
        }
    }

    @MutationMapping
    public FeedPayload removeJobFromFeed(@Argument String feedId, @Argument String jobId) {
        Actor actor = api.requireScope(TokenScope.FEEDS_WRITE);
        return ok(feedService.removeJob(uuid(feedId, "feed"), uuid(jobId, "job"), actor), actor);
    }

    private FeedPayload ok(Feed feed, Actor actor) {
        return FeedPayload.ok(mapper.readFeed(feed.getId(), actor));
    }

    private static String str(Map<String, Object> input, String key) {
        Object value = input.get(key);
        return value == null ? null : value.toString();
    }
}
