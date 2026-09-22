package org.letsemploy.ojobpub_publisher.api;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.letsemploy.ojobpub_publisher.api.ApiTypes.LocationDto;
import org.letsemploy.ojobpub_publisher.api.ApiTypes.TagDto;
import org.letsemploy.ojobpub_publisher.location.LocationService;
import org.letsemploy.ojobpub_publisher.tag.TagService;
import org.letsemploy.ojobpub_publisher.token.TokenScope;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

/**
 * Locations and tags (spec 11.3).
 *
 * <p>Both are platform-wide rather than an employer's own, and {@code JobInput}
 * refers to them by id, so without these a caller could not write a job at all.
 * They are gated on {@code jobs:read} for that reason: they exist to fill a
 * posting in.
 */
@Controller
@RequiredArgsConstructor
public class ReferenceApiController {

    private final LocationService locationService;
    private final TagService tagService;
    private final ApiMapper mapper;
    private final ApiActor api;

    @QueryMapping
    @Transactional(readOnly = true)
    public List<LocationDto> locations(@Argument String q) {
        api.requireScope(TokenScope.JOBS_READ);
        return (q == null || q.isBlank() ? locationService.findAll() : locationService.search(q))
                .stream().map(mapper::location).toList();
    }

    @QueryMapping
    @Transactional(readOnly = true)
    public List<TagDto> tags(@Argument String q) {
        api.requireScope(TokenScope.JOBS_READ);
        return (q == null || q.isBlank() ? tagService.findAll() : tagService.search(q))
                .stream().map(mapper::tag).toList();
    }
}
