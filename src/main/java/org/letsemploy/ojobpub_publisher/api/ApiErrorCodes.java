package org.letsemploy.ojobpub_publisher.api;

import graphql.GraphQLError;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.graphql.server.WebGraphQlInterceptor;
import org.springframework.graphql.server.WebGraphQlRequest;
import org.springframework.graphql.server.WebGraphQlResponse;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * Guarantees the invariant of spec 11.4: every entry in the {@code errors} array
 * carries an {@code extensions.code}.
 *
 * <p>Our own faults set one ({@link ApiExceptionResolver}), but graphql-java
 * raises plenty that do not - a syntax error, an unknown field, an exceeded
 * depth limit. Rather than enumerate them, anything arriving without a code gets
 * one derived from its error type, so a client can always branch on
 * {@code extensions.code} and never has to match message text.
 */
@Component
public class ApiErrorCodes implements WebGraphQlInterceptor {

    @Override
    public Mono<WebGraphQlResponse> intercept(WebGraphQlRequest request, Chain chain) {
        return chain.next(request).map(response -> {
            List<GraphQLError> raw = response.getExecutionResult().getErrors();
            if (raw.isEmpty()) {
                return response;
            }
            List<GraphQLError> coded = raw.stream().map(ApiErrorCodes::withCode).toList();
            return response.transform(builder -> builder.errors(coded));
        });
    }

    private static GraphQLError withCode(GraphQLError error) {
        Map<String, Object> extensions = error.getExtensions() == null
                ? Map.of() : error.getExtensions();
        if (extensions.containsKey("code")) {
            return error;
        }
        Map<String, Object> merged = new LinkedHashMap<>(extensions);
        merged.put("code", codeFor(error));
        return GraphQLError.newError()
                .errorType(error.getErrorType())
                .message(error.getMessage())
                .path(error.getPath())
                .locations(error.getLocations())
                .extensions(merged)
                .build();
    }

    private static String codeFor(GraphQLError error) {
        String type = error.getErrorType() == null ? "" : error.getErrorType().toString();
        return switch (type) {
            case "InvalidSyntax" -> "SYNTAX_ERROR";
            case "ValidationError" -> "VALIDATION_ERROR";
            case "ExecutionAborted" -> "LIMIT_EXCEEDED";
            case "BAD_REQUEST" -> "BAD_REQUEST";
            case "UNAUTHORIZED" -> "UNAUTHENTICATED";
            case "FORBIDDEN" -> "FORBIDDEN";
            case "NOT_FOUND" -> "NOT_FOUND";
            default -> "INTERNAL_ERROR";
        };
    }
}
