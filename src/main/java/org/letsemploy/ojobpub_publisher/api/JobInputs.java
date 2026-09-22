package org.letsemploy.ojobpub_publisher.api;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.job.JobForm;

/**
 * {@code JobInput} to the {@link JobForm} the service already validates (spec 11.2).
 *
 * <p>The form is the web layer's binding object, and the API reuses it so that
 * both callers go through one validation. The only work here is shape: GraphQL
 * enums arrive as their upper-case names, the form holds the lower-case
 * spellings, and dates arrive as strings.
 */
final class JobInputs {

    private JobInputs() {
    }

    static JobForm toForm(Map<String, Object> input) {
        JobForm form = new JobForm();
        form.setTitle(str(input, "title"));
        form.setDescription(str(input, "description"));
        form.setUrl(str(input, "url"));
        form.setLanguage(str(input, "language"));
        form.setReferenceId(str(input, "referenceId"));
        form.setCategory(str(input, "category"));
        form.setJobType(lower(input, "jobType"));
        form.setWorkType(lower(input, "workType"));
        form.setExperienceLevel(lower(input, "experienceLevel"));
        form.setWorkLoadPercentMin(integer(input, "workLoadPercentMin"));
        form.setWorkLoadPercentMax(integer(input, "workLoadPercentMax"));
        form.setSalaryMin(decimal(input, "salaryMin"));
        form.setSalaryMax(decimal(input, "salaryMax"));
        form.setSalaryCurrency(str(input, "salaryCurrency"));
        form.setSalaryInterval(lower(input, "salaryInterval"));
        form.setStartDate(date(input, "startDate"));
        form.setEndDate(date(input, "endDate"));
        form.setApplyBefore(date(input, "applyBefore"));
        form.setLocations(locationIds(input));
        form.setTags(tagIds(input));
        return form;
    }

    private static String str(Map<String, Object> input, String key) {
        Object value = input.get(key);
        return value == null ? null : value.toString();
    }

    /**
     * {@code ON_SITE} in the schema is {@code on_site} on the form. Note this is
     * the form's own spelling and not the published one - the published document
     * says {@code on-site}, which {@code OjobpubEnums} maps explicitly (spec 6.5).
     */
    private static String lower(Map<String, Object> input, String key) {
        String value = str(input, key);
        return value == null ? null : value.toLowerCase();
    }

    private static Integer integer(Map<String, Object> input, String key) {
        Object value = input.get(key);
        return value == null ? null : ((Number) value).intValue();
    }

    private static BigDecimal decimal(Map<String, Object> input, String key) {
        Object value = input.get(key);
        if (value == null) {
            return null;
        }
        return value instanceof BigDecimal d ? d : new BigDecimal(value.toString());
    }

    private static LocalDate date(Map<String, Object> input, String key) {
        String value = str(input, key);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value);
        } catch (DateTimeParseException e) {
            // A bad date is the caller's mistake, so it belongs in userErrors
            // with every other rejected field, not in the errors array.
            throw new ValidationFailure(key, "Expected a date as YYYY-MM-DD.");
        }
    }

    private static List<UUID> locationIds(Map<String, Object> input) {
        List<UUID> ids = new ArrayList<>();
        for (Object raw : list(input, "locationIds")) {
            ids.add(JobApiController.uuid(raw.toString(), "location"));
        }
        return ids;
    }

    private static List<Long> tagIds(Map<String, Object> input) {
        List<Long> ids = new ArrayList<>();
        for (Object raw : list(input, "tagIds")) {
            try {
                ids.add(Long.valueOf(raw.toString()));
            } catch (NumberFormatException e) {
                throw new ValidationFailure("tagIds", "No such tag: " + raw);
            }
        }
        return ids;
    }

    @SuppressWarnings("unchecked")
    private static List<Object> list(Map<String, Object> input, String key) {
        Object value = input.get(key);
        return value == null ? List.of() : (List<Object>) value;
    }
}
