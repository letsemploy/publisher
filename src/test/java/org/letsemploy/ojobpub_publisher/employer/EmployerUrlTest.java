package org.letsemploy.ojobpub_publisher.employer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

/**
 * The employer's URL is published in every feed (spec 3.1), so the service takes
 * only an absolute http(s) address - the API has no browser checking the field.
 */
@SpringBootTest
@ActiveProfiles(resolver = TestProfiles.class)
@Transactional
class EmployerUrlTest {

    private static final UUID ACME = UUID.fromString("003d6aec-021b-11f1-aefa-f649a5d91690");

    @Autowired
    private EmployerService employerService;
    @Autowired
    private EmployerRepo employerRepo;

    private final Actor owner = Actor.user(UUID.randomUUID(), "Owner", null, false,
            Map.of(ACME, MembershipRole.OWNER));

    private Employer save(String url) {
        Employer acme = employerRepo.findById(ACME).orElseThrow();
        return employerService.save(ACME, acme.getName(), acme.getSlug(), url, acme.getIndustry(),
                Headquarters.existing(acme.getHeadquarters().getId()), owner);
    }

    @Test
    void aScriptLinkIsRefusedOnTheUrlField() {
        assertThatThrownBy(() -> save("javascript:alert(1)"))
                .isInstanceOf(ValidationFailure.class)
                .satisfies(e -> assertThat(((ValidationFailure) e).fields()).containsExactly("url"));
    }

    @Test
    void anHttpsAddressOrNoneIsTaken() {
        assertThat(save("https://acme.example").getUrl()).isEqualTo("https://acme.example");
        assertThat(save(" ").getUrl()).isNull();
    }
}
