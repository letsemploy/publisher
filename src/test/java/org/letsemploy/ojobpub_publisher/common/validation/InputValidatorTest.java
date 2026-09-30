package org.letsemploy.ojobpub_publisher.common.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.job.JobForm;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.autoconfigure.context.MessageSourceAutoConfiguration;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Input shape through Bean Validation (spec 9.6): refused as field errors keyed by
 * the field the user typed into, in the request's language (spec 8.1, 8.2).
 */
@SpringJUnitConfig(InputValidator.class)
@ImportAutoConfiguration({ValidationAutoConfiguration.class, MessageSourceAutoConfiguration.class})
class InputValidatorTest {

    @Autowired
    private InputValidator inputs;

    @AfterEach
    void resetLocale() {
        LocaleContextHolder.resetLocaleContext();
    }

    @Test
    void aViolationIsAValidationFailureKeyedByItsField() {
        assertThatThrownBy(() -> inputs.check(new NameInput(" ")))
                .isInstanceOf(ValidationFailure.class)
                .satisfies(e -> assertThat(((ValidationFailure) e).getFieldErrors())
                        .containsExactly(Map.entry("name", "A name is required.")));
        inputs.check(new NameInput("Engineering"));
    }

    @Test
    void messagesFollowTheRequestLocaleAndFillInTheConstraintsLimits() {
        LocaleContextHolder.setLocale(Locale.ENGLISH);
        JobForm form = validJob();
        form.setDescription("x".repeat(1001));
        assertThat(inputs.violations(form)).containsEntry("description",
                "At most 1000 characters; the published schema caps it there.");

        LocaleContextHolder.setLocale(Locale.GERMAN);
        assertThat(inputs.violations(form).get("description")).startsWith("Höchstens 1000 Zeichen");
    }

    /** Cross-field rules land beside the field that fixes them, under the name the API maps. */
    @Test
    void jobFormCrossFieldRulesAreReportedOnTheFieldThatFixesThem() {
        JobForm form = validJob();
        form.setSalaryMin(new BigDecimal("90000"));
        form.setSalaryMax(new BigDecimal("80000"));
        form.setWorkLoadPercentMin(100);
        form.setWorkLoadPercentMax(80);
        form.setStartDate(LocalDate.of(2026, 10, 1));
        form.setEndDate(LocalDate.of(2026, 9, 1));

        assertThat(inputs.violations(form)).containsOnlyKeys(
                "salaryCurrency", "salaryInterval", "salaryMax", "workLoadPercentMax", "endDate");
    }

    @Test
    void aWellFormedJobPasses() {
        assertThat(inputs.violations(validJob())).isEmpty();
    }

    @Test
    void theUrlMustBeAbsolute() {
        JobForm form = validJob();
        form.setUrl("example.com/jobs/1");
        assertThat(inputs.violations(form)).containsOnlyKeys("url");
    }

    private static JobForm validJob() {
        JobForm form = new JobForm();
        form.setTitle("Engineer");
        form.setUrl("https://example.com/jobs/1");
        form.setLanguage("en");
        form.setJobType("permanent");
        return form;
    }
}
