package org.letsemploy.ojobpub_publisher.tag;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TagService.class)
class TagServiceTest {

    @Autowired
    private TagService tagService;

    @Autowired
    private TagRepo tagRepo;

    @Test
    void updateShouldAllowChangingTagName() {
        Tag tag = tagRepo.save(new Tag("original"));

        Tag updated = tagService.save(tag.getId(), "updated");

        assertThat(updated.getName()).isEqualTo("updated");
        assertThat(tagRepo.findById(tag.getId())).get().extracting(Tag::getName).isEqualTo("updated");
    }

    /** Tags are normalized on input: trimmed and lower-cased (spec 3.4). */
    @Test
    void namesAreNormalised() {
        Tag tag = tagService.save(null, "  Terraform  ");
        assertThat(tag.getName()).isEqualTo("terraform");
    }

    @Test
    void duplicateNameIsRejected() {
        tagService.save(null, "ansible");
        assertThatThrownBy(() -> tagService.save(null, "ANSIBLE"))
                .isInstanceOf(ValidationFailure.class);
    }

    /** The published schema caps a tag at 28 characters (spec 3.4). */
    @Test
    void overlongNameIsRejected() {
        assertThatThrownBy(() -> tagService.save(null, "x".repeat(29)))
                .isInstanceOf(ValidationFailure.class);
    }

    /** Creating a tag that already exists returns the existing one, not an error. */
    @Test
    void findOrCreateIsIdempotent() {
        Tag first = tagService.findOrCreate("podman");
        Tag second = tagService.findOrCreate("Podman");
        assertThat(second.getId()).isEqualTo(first.getId());
    }
}
