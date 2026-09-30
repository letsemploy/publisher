package org.letsemploy.ojobpub_publisher.ojobpub.v1.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;
import java.util.List;

/**
 * Root of the published document (spec 6.1).
 *
 * <p>The schema is closed ({@code additionalProperties: false}), so no field
 * outside this mapping may be emitted and a null is omitted, never serialized.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"version", "lastUpdated", "employer", "jobs"})
public record OjobpubDto(
        String version,
        /*
         * An Instant serializes to RFC 3339 with an offset. A LocalDateTime
         * would emit no offset and fail the schema's date-time format (spec 6.1).
         */
        Instant lastUpdated,
        OjobpubEmployerDto employer,
        List<OjobpubJobDto> jobs) {

    public static final String VERSION = "1.0";

    /** The version is the contract's, never the caller's; a document read back keeps its own. */
    public OjobpubDto {
        version = version == null ? VERSION : version;
        jobs = jobs == null ? List.of() : jobs;
    }

    public OjobpubDto(Instant lastUpdated, OjobpubEmployerDto employer, List<OjobpubJobDto> jobs) {
        this(VERSION, lastUpdated, employer, jobs);
    }

    /** The same document dated otherwise, for a permalink whose own change is later (spec 5.5). */
    public OjobpubDto withLastUpdated(Instant lastUpdated) {
        return new OjobpubDto(version, lastUpdated, employer, jobs);
    }
}
