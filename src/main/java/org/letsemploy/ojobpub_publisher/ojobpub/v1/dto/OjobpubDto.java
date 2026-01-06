package org.letsemploy.ojobpub_publisher.ojobpub.v1.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * Root of the published document (spec 6.1).
 *
 * <p>The schema is closed ({@code additionalProperties: false}), so no field
 * outside this mapping may be emitted and a null is omitted, never serialized.
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonPropertyOrder({"version", "lastUpdated", "employer", "jobs"})
public class OjobpubDto {

    public static final String VERSION = "1.0";

    private final String version = VERSION;

    /**
     * An {@link Instant} serializes to RFC 3339 with an offset. A LocalDateTime
     * would emit no offset and fail the schema's date-time format (spec 6.1).
     */
    private Instant lastUpdated;

    private OjobpubEmployerDto employer;

    private List<OjobpubJobDto> jobs = new ArrayList<>();
}
