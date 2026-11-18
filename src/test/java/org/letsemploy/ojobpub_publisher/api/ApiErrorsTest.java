package org.letsemploy.ojobpub_publisher.api;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.springframework.context.support.StaticMessageSource;

/** A refusal names the API's own argument, not the form's field (spec 11.4); no Spring. */
class ApiErrorsTest {

    private final ApiErrors errors = new ApiErrors(new StaticMessageSource());

    @Test
    void theHeadquartersIsNamedAsTheInputNamesIt() {
        assertThat(errors.from(new ValidationFailure("headquarters", "Choose one of yours.")))
                .extracting(UserError::field).containsExactly("headquartersId");
    }
}
