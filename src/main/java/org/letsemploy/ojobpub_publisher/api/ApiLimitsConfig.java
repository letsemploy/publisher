package org.letsemploy.ojobpub_publisher.api;

import graphql.analysis.MaxQueryComplexityInstrumentation;
import graphql.analysis.MaxQueryDepthInstrumentation;
import graphql.execution.instrumentation.Instrumentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Depth and complexity ceilings, refusing an over-large query before it executes
 * (spec 11.6).
 *
 * <p>GraphQL's flexibility is otherwise an invitation to ask for the transitive
 * closure of the data in one request: {@code feeds { jobs { … } }} nests as deep
 * as a caller cares to write it, and each level multiplies the work.
 *
 * <p>The numbers are generous for any query a documented client would send. The
 * deepest legitimate shape in this schema is roughly
 * {@code feeds > jobs > locations > city}, which is four.
 */
@Configuration
public class ApiLimitsConfig {

    @Bean
    Instrumentation maxQueryDepth(@Value("${app.api.max-query-depth:10}") int maxDepth) {
        return new MaxQueryDepthInstrumentation(maxDepth);
    }

    @Bean
    Instrumentation maxQueryComplexity(@Value("${app.api.max-query-complexity:1000}") int max) {
        return new MaxQueryComplexityInstrumentation(max);
    }
}
