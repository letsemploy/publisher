package org.letsemploy.ojobpub_publisher.api;

import static org.letsemploy.ojobpub_publisher.api.JobApiController.uuid;

import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.letsemploy.ojobpub_publisher.api.ApiTypes.*;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.employer.EmployerService;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.token.TokenScope;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one employer a token belongs to (spec 11.2).
 *
 * <p>There is no {@code createEmployer} and no {@code deleteEmployer}. A token
 * exists inside an employer, so it has no standing to create a sibling, and
 * deleting the employer would destroy the credential's own context - both are
 * acts for a person on the back-office screens.
 */
@Controller
@RequiredArgsConstructor
public class EmployerApiController {

    private final EmployerService employerService;
    private final ApiMapper mapper;
    private final ApiActor api;
    private final ApiErrors errors;

    /**
     * Readable by any token: knowing the name of the employer you were issued
     * for discloses nothing, and a scope check here would only make every
     * integration ask for a scope it does not otherwise need.
     */
    @QueryMapping
    @Transactional(readOnly = true)
    public EmployerDto employer() {
        Actor actor = api.current();
        return mapper.employer(employerService.findVisible(api.employerId(), actor));
    }

    /**
     * Editing the employer record takes {@code people:write}. There is no
     * separate employer scope: administering the employer - its record and its
     * people - is one responsibility and already takes an owner membership,
     * which {@link EmployerService#save} enforces (spec 2.1).
     *
     * <p>An omitted {@code headquartersId} keeps the current one. The service
     * requires a headquarters because the published document carries it, so
     * passing null through would reject every partial update.
     */
    @MutationMapping
    public EmployerPayload updateEmployer(@Argument Map<String, Object> input) {
        Actor actor = api.requireScope(TokenScope.PEOPLE_WRITE);
        UUID employerId = api.employerId();
        Employer current = employerService.findVisible(employerId, actor);
        Object headquarters = input.get("headquartersId");
        try {
            employerService.save(
                    employerId,
                    str(input, "name"), str(input, "slug"), str(input, "url"),
                    str(input, "industry"),
                    headquarters == null
                            ? (current.getHeadquarters() == null
                                    ? null : current.getHeadquarters().getId())
                            : uuid(headquarters.toString(), "location"),
                    actor);
            return EmployerPayload.ok(mapper.readEmployer(employerId, actor));
        } catch (ValidationFailure e) {
            return new EmployerPayload(null, errors.from(e));
        }
    }

    private static String str(Map<String, Object> input, String key) {
        Object value = input.get(key);
        return value == null ? null : value.toString();
    }
}
