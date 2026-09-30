package org.letsemploy.ojobpub_publisher.job;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The job form's cross-field rules (spec 3.3): a salary amount needs its currency
 * and interval, and no range may run backwards. Each is reported on the field
 * that fixes it, so it appears beside that field and keeps its API name.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = JobFormShape.Validator.class)
@interface JobFormShape {

    String message() default "";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<JobFormShape, JobForm> {
        @Override
        public boolean isValid(JobForm form, ConstraintValidatorContext context) {
            context.disableDefaultConstraintViolation();
            boolean valid = true;
            // A bare amount is not interpretable by a consumer (spec 3.3).
            if (form.isSalaryPresent()) {
                if (isBlank(form.getSalaryCurrency())) {
                    valid = refuse(context, "salaryCurrency", "{validation.salary.partRequired}");
                }
                if (isBlank(form.getSalaryInterval())) {
                    valid = refuse(context, "salaryInterval", "{validation.salary.partRequired}");
                }
            }
            if (form.getSalaryMin() != null && form.getSalaryMax() != null
                    && form.getSalaryMin().compareTo(form.getSalaryMax()) > 0) {
                valid = refuse(context, "salaryMax", "{validation.range.maxBelowMin}");
            }
            if (form.getWorkLoadPercentMin() != null && form.getWorkLoadPercentMax() != null
                    && form.getWorkLoadPercentMin() > form.getWorkLoadPercentMax()) {
                valid = refuse(context, "workLoadPercentMax", "{validation.range.maxBelowMin}");
            }
            if (form.getStartDate() != null && form.getEndDate() != null
                    && form.getEndDate().isBefore(form.getStartDate())) {
                valid = refuse(context, "endDate", "{validation.endDate.beforeStart}");
            }
            return valid;
        }

        private static boolean refuse(ConstraintValidatorContext context, String field, String message) {
            context.buildConstraintViolationWithTemplate(message).addPropertyNode(field).addConstraintViolation();
            return false;
        }

        private static boolean isBlank(String s) {
            return s == null || s.isBlank();
        }
    }
}
