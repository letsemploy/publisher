package org.letsemploy.ojobpub_publisher.api;

import graphql.GraphQLError;
import graphql.schema.DataFetchingEnvironment;
import java.util.List;
import java.util.Map;
import org.letsemploy.ojobpub_publisher.common.exception.NotFoundException;
import org.springframework.graphql.execution.DataFetcherExceptionResolverAdapter;
import org.springframework.graphql.execution.ErrorType;
import org.springframework.stereotype.Component;

/**
 * Faults become entries in the GraphQL errors array with a stable
 * `extensions.code` (spec 11.4). Domain failures do not come through here - they
 * are data, returned in each payload's userErrors.
 */
@Component
public class ApiExceptionResolver extends DataFetcherExceptionResolverAdapter {

    @Override
    protected List<GraphQLError> resolveToMultipleErrors(Throwable ex, DataFetchingEnvironment env) {
        if (ex instanceof ApiForbiddenException) {
            return List.of(error(ex.getMessage(), "FORBIDDEN", ErrorType.FORBIDDEN, env));
        }
        if (ex instanceof NotFoundException) {
            return List.of(error(ex.getMessage(), "NOT_FOUND", ErrorType.NOT_FOUND, env));
        }
        return null;
    }

    private GraphQLError error(String message, String code, ErrorType type,
                               DataFetchingEnvironment env) {
        return GraphQLError.newError()
                .errorType(type)
                .message(message)
                .path(env.getExecutionStepInfo().getPath())
                .location(env.getField().getSourceLocation())
                .extensions(Map.of("code", code))
                .build();
    }
}
