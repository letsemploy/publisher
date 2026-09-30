package org.letsemploy.ojobpub_publisher.api;

import java.util.List;
import java.util.UUID;
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
 * <p>Both belong to an employer (spec 3.2, 3.4), so a token sees its own
 * employer's and nobody else's. {@code JobInput} refers to them by id, so without
 * these a caller could not write a job at all; they are gated on {@code jobs:read}
 * for that reason: they exist to fill a posting in.
 */
@Controller
public class ReferenceApiController {

    private final LocationService locationService;
    private final TagService tagService;
    private final ApiMapper mapper;
    private final ApiActor api;

    public ReferenceApiController(LocationService locationService,
                                  TagService tagService,
                                  ApiMapper mapper,
                                  ApiActor api) {
        this.locationService = locationService;
        this.tagService = tagService;
        this.mapper = mapper;
        this.api = api;
    }

    @QueryMapping
    @Transactional(readOnly = true)
    public List<LocationDto> locations(@Argument String q) {
        api.requireScope(TokenScope.JOBS_READ);
        UUID employerId = api.employerId();
        return (q == null || q.isBlank() ? locationService.ofEmployer(employerId)
                : locationService.search(employerId, q))
                .stream().map(mapper::location).toList();
    }

    @QueryMapping
    @Transactional(readOnly = true)
    public List<TagDto> tags(@Argument String q) {
        api.requireScope(TokenScope.JOBS_READ);
        UUID employerId = api.employerId();
        return (q == null || q.isBlank() ? tagService.ofEmployer(employerId)
                : tagService.search(List.of(employerId), q))
                .stream().map(mapper::tag).toList();
    }
}
