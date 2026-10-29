package org.letsemploy.ojobpub_publisher.ojobpub.v1.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.stereotype.Component;
import tools.jackson.databind.cfg.DateTimeFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes the published document (spec 6): the one place its bytes are made, for
 * the endpoint, its ETag, the validator and the feed screen's preview alike, so
 * none of them can show something the others do not.
 *
 * <p>A mapper of its own, configured here and nowhere else. Boot's shared one
 * follows {@code spring.jackson.*}, which a deployment may set for other
 * reasons, and the contract must not move with it. It is deliberately not a
 * {@code JsonMapper} bean either: Boot would then stop configuring its own, and
 * the omitted nulls below would reach the GraphQL responses, where a null must
 * be present.
 *
 * <p>Dates must be ISO-8601 text, never numeric timestamps: the schema requires
 * {@code lastUpdated} to be an RFC 3339 date-time, and an epoch number would fail
 * validation while looking harmless.
 */
@Component
public class OjobpubJson {

    private final JsonMapper mapper = JsonMapper.builder()
            .disable(DateTimeFeature.WRITE_DATES_AS_TIMESTAMPS)
            .changeDefaultPropertyInclusion(inclusion -> inclusion.withValueInclusion(JsonInclude.Include.NON_NULL))
            .build();

    /** The document as served. */
    public byte[] write(Object document) {
        return mapper.writeValueAsBytes(document);
    }

    /** The same document, indented, for the feed screen's preview (spec 7.12). */
    public String writePretty(Object document) {
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(document);
    }

    /** Reads a document back, for tests and anything that checks what was served. */
    public <T> T read(byte[] json, Class<T> type) {
        return mapper.readValue(json, type);
    }
}
