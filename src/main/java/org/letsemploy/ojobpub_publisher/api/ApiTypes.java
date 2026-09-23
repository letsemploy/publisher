package org.letsemploy.ojobpub_publisher.api;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The shapes the schema in {@code graphql/schema.graphqls} describes (spec 11.3).
 *
 * <p>Entities are mapped onto these rather than returned directly. An entity is
 * a database row with lazy associations and a Java name of its own; the API is a
 * contract. Mapping keeps the two free to differ, and keeps every value that
 * leaves here rendered deliberately.
 */
public final class ApiTypes {

    private ApiTypes() {
    }

    public record LocationDto(String id, String city, String country) {
    }

    public record TagDto(String id, String name) {
    }

    public record EmployerDto(String id, String name, String slug, String url, String industry,
                              LocationDto headquarters) {
    }

    public record JobDto(String id, String title, String description, String url, String language,
                         String referenceId, String category, String jobType, String workType,
                         String experienceLevel, Integer workLoadPercentMin,
                         Integer workLoadPercentMax, BigDecimal salaryMin, BigDecimal salaryMax,
                         String salaryCurrency, String salaryInterval, String startDate,
                         String endDate, String applyBefore, String status, String publishedAt,
                         String presentation, List<LocationDto> locations, List<String> tags) {
    }

    public record JobPageDto(List<JobDto> content, int page, int size, long totalElements,
                             int totalPages) {
    }

    public record FeedDto(String id, String name, String slug, String description, String publicUrl,
                          List<JobDto> jobs) {
    }

    public record MemberDto(String userId, String displayName, String email, String role,
                            boolean suspended, String suspendedAt) {
    }

    public record InvitationDto(String id, String inviteeEmail, String role, String status,
                                String invitedBy) {
    }

    // --- payloads -----------------------------------------------------------
    // Each carries the thing and the errors. A payload with a null thing and an
    // empty userErrors list never happens: one of the two is always populated.

    public record JobPayload(JobDto job, List<UserError> userErrors) {
        public static JobPayload ok(JobDto job) {
            return new JobPayload(job, List.of());
        }
    }

    public record FeedPayload(FeedDto feed, List<UserError> userErrors) {
        public static FeedPayload ok(FeedDto feed) {
            return new FeedPayload(feed, List.of());
        }
    }

    public record EmployerPayload(EmployerDto employer, List<UserError> userErrors) {
        public static EmployerPayload ok(EmployerDto employer) {
            return new EmployerPayload(employer, List.of());
        }
    }

    public record InvitationPayload(String outcome, List<UserError> userErrors) {
        public static InvitationPayload ok(String outcome) {
            return new InvitationPayload(outcome, List.of());
        }
    }

    public record MemberPayload(MemberDto member, List<UserError> userErrors) {
        public static MemberPayload ok(MemberDto member) {
            return new MemberPayload(member, List.of());
        }
    }

    public record DeletePayload(String deletedId, List<UserError> userErrors) {
        public static DeletePayload ok(UUID id) {
            return new DeletePayload(id.toString(), List.of());
        }
    }
}
