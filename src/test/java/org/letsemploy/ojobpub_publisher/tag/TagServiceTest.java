package org.letsemploy.ojobpub_publisher.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.employer.EmployerRepo;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/** An employer's tags (spec 3.4): normalised, capped, and unique within the employer. */
@DataJpaTest
@ActiveProfiles(resolver = TestProfiles.WithoutDev.class)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TagService.class)
class TagServiceTest {

    /** Acme, from the seed. */
    private static final UUID ACME = UUID.fromString("003d6aec-021b-11f1-aefa-f649a5d91690");

    @Autowired
    private TagService tagService;
    @Autowired
    private TagRepo tagRepo;
    @Autowired
    private EmployerRepo employerRepo;

    private Employer acme;
    private final Actor admin = Actor.user(UUID.randomUUID(), "Admin", "admin@example.com", true, Map.of());

    @BeforeEach
    void setUp() {
        acme = employerRepo.findById(ACME).orElseThrow();
    }

    /**
     * A second employer, saved directly: this slice has no EmployerService, and the
     * headquarters may be null in the database until the service sets it (spec 3.1).
     */
    private Employer another() {
        Employer other = new Employer();
        other.setName("Other Co");
        other.setSlug("other-co");
        return employerRepo.save(other);
    }

    @Test
    void updateShouldAllowChangingTagName() {
        Tag tag = tagRepo.save(new Tag(acme, "original"));

        Tag updated = tagService.update(tag.getId(), "updated", admin);

        assertThat(updated.getName()).isEqualTo("updated");
        assertThat(tagRepo.findById(tag.getId())).get().extracting(Tag::getName).isEqualTo("updated");
    }

    /** Tags are normalized on input: trimmed and lower-cased (spec 3.4). */
    @Test
    void namesAreNormalised() {
        Tag tag = tagService.create(acme, "  Terraform  ");
        assertThat(tag.getName()).isEqualTo("terraform");
    }

    @Test
    void duplicateNameIsRejectedWithinAnEmployer() {
        tagService.create(acme, "ansible");
        assertThatThrownBy(() -> tagService.create(acme, "ANSIBLE"))
                .isInstanceOf(ValidationFailure.class);
    }

    /** Unique within the employer, not across the installation: another may use the name. */
    @Test
    void anotherEmployerMayUseTheSameName() {
        tagService.create(acme, "ansible");
        Tag theirs = tagService.create(another(), "ansible");
        assertThat(theirs.getId()).isNotNull();
    }

    /** The published schema caps a tag at 28 characters (spec 3.4). */
    @Test
    void overlongNameIsRejected() {
        assertThatThrownBy(() -> tagService.create(acme, "x".repeat(29)))
                .isInstanceOf(ValidationFailure.class);
    }

    /** Creating a tag the employer already has returns the existing one, not an error. */
    @Test
    void findOrCreateIsIdempotent() {
        Tag first = tagService.findOrCreate(acme, "podman");
        Tag second = tagService.findOrCreate(acme, "Podman");
        assertThat(second.getId()).isEqualTo(first.getId());
    }
}
