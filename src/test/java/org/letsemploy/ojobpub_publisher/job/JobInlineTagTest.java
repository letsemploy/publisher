package org.letsemploy.ojobpub_publisher.job;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.common.exception.ValidationFailure;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.employer.EmployerRepo;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.tag.Tag;
import org.letsemploy.ojobpub_publisher.tag.TagRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Tags created from the job form's tag search (spec 7.8): offered for a name the
 * employer lacks, created only when the job saves, and never by a refused save.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.class)
@Transactional
class JobInlineTagTest {

    /** Fixed ids from the seed: Acme, and its "java" tag. */
    private static final UUID ACME = UUID.fromString("003d6aec-021b-11f1-aefa-f649a5d91690");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private JobService jobService;
    @Autowired
    private TagRepo tagRepo;
    @Autowired
    private EmployerRepo employerRepo;

    /** An editor of Acme, synthetic: the audit log has no foreign keys. */
    private final Actor editor = Actor.user(UUID.randomUUID(), "Erin Editor", null, false,
            Map.of(ACME, MembershipRole.EDITOR));

    private JobForm form(String title, String... newTags) {
        JobForm form = new JobForm();
        form.setTitle(title);
        form.setUrl("https://example.com/jobs/1");
        form.setLanguage("en");
        form.setJobType("permanent");
        form.setNewTags(new ArrayList<>(List.of(newTags)));
        return form;
    }

    private Employer acme() {
        return employerRepo.findById(ACME).orElseThrow();
    }

    private boolean acmeHas(String name) {
        return tagRepo.findFirstByEmployerIdAndNameIgnoreCase(ACME, name).isPresent();
    }

    @Test
    void aNewNameIsCreatedForTheJobsEmployerAndCarried() {
        String name = "inline-" + UUID.randomUUID().toString().substring(0, 8);
        Job job = jobService.save(form("Rust developer", "  " + name.toUpperCase() + " "), acme(), false, editor);

        assertThat(job.getTags()).extracting(Tag::getName).containsExactly(name);
        Tag created = tagRepo.findFirstByEmployerIdAndNameIgnoreCase(ACME, name).orElseThrow();
        assertThat(created.getEmployer().getId()).isEqualTo(ACME);
    }

    @Test
    void anExistingNameIsThatTagAndNotASecond() {
        Tag java = tagRepo.findFirstByEmployerIdAndNameIgnoreCase(ACME, "java").orElseThrow();
        JobForm form = form("Java developer", "Java", "java");
        form.setTags(List.of(java.getId()));

        Job job = jobService.save(form, acme(), false, editor);

        assertThat(job.getTags()).extracting(Tag::getId).containsExactly(java.getId());
        assertThat(tagRepo.findByEmployerIdOrderByNameAsc(ACME))
                .filteredOn(t -> t.getName().equals("java")).hasSize(1);
    }

    @Test
    void aTooLongNameIsAnErrorOnTheTagsField() {
        assertThatThrownBy(() -> jobService.save(form("Job", "x".repeat(Tag.MAX_LENGTH + 1)), acme(), false, editor))
                .isInstanceOf(ValidationFailure.class)
                .satisfies(e -> assertThat(((ValidationFailure) e).fields()).containsExactly("tags"));
    }

    @Test
    void newTagsCountTowardsTheLimitPerJob() {
        String[] names = new String[Tag.MAX_PER_JOB + 1];
        String run = UUID.randomUUID().toString().substring(0, 8);
        for (int i = 0; i < names.length; i++) {
            names[i] = "many-" + run + "-" + i;
        }
        assertThatThrownBy(() -> jobService.save(form("Job", names), acme(), false, editor))
                .isInstanceOf(ValidationFailure.class)
                .satisfies(e -> assertThat(((ValidationFailure) e).fields()).contains("tags"));
        // Refused before any was created, not only rolled back afterwards.
        for (String name : names) {
            assertThat(acmeHas(name)).as(name).isFalse();
        }
    }

    /** Outside the test's transaction, so the save's own rollback is what is observed. */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aRefusedSaveCreatesNoTag() {
        String name = "refused-" + UUID.randomUUID().toString().substring(0, 8);
        assertThatThrownBy(() -> jobService.save(form("", name), acme(), false, editor))
                .isInstanceOf(ValidationFailure.class);
        assertThat(acmeHas(name)).isFalse();
    }

    // ------------------------------------------------------------ the search

    private String search(String q) throws Exception {
        return mvc.perform(get("/jobs/tags/search").param("employer", ACME.toString()).param("q", q))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    @Test
    void theSearchOffersAnUnknownNameForCreation() throws Exception {
        String html = search("Brand New");
        assertThat(html).contains("data-name=\"newTags\"").contains("data-value=\"brand new\"");
    }

    @Test
    void theSearchDoesNotOfferANameTheEmployerHas() throws Exception {
        assertThat(search("JAVA")).contains("data-value=\"1\"").doesNotContain("newTags");
    }

    @Test
    void theSearchOffersNothingForAnEmptyOrTooLongQuery() throws Exception {
        assertThat(search("  ")).doesNotContain("newTags");
        assertThat(search("x".repeat(Tag.MAX_LENGTH + 1))).doesNotContain("newTags");
    }

    @Test
    void theLocationSearchNeverOffersCreation() throws Exception {
        String html = mvc.perform(get("/jobs/locations/search").param("employer", ACME.toString())
                        .param("q", "Nowhere"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).doesNotContain("newTags");
    }

    @Test
    void aRefusedFormKeepsTheNewTagChip() throws Exception {
        String html = mvc.perform(post("/jobs/create").param("title", "").param("url", "https://example.com")
                        .param("language", "en").param("jobType", "permanent").param("newTags", "Fresh Tag"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(html).containsPattern("name=\"newTags\"\\s+value=\"fresh tag\"");
    }
}
