package org.letsemploy.ojobpub_publisher.location;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.persistence.EntityManager;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.TestProfiles;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.employer.EmployerRepo;
import org.letsemploy.ojobpub_publisher.employer.EmployerService;
import org.letsemploy.ojobpub_publisher.employer.Headquarters;
import org.letsemploy.ojobpub_publisher.job.JobRepo;
import org.letsemploy.ojobpub_publisher.membership.MembershipRole;
import org.letsemploy.ojobpub_publisher.security.Actor;
import org.letsemploy.ojobpub_publisher.security.CurrentUserService;
import org.letsemploy.ojobpub_publisher.security.UserEntity;
import org.letsemploy.ojobpub_publisher.security.UserRepo;
import org.letsemploy.ojobpub_publisher.tag.TagRepo;
import org.letsemploy.ojobpub_publisher.tag.TagService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Locations and tags belong to one employer (spec 3.2, 3.3, 3.4).
 *
 * <p>Acts as the owner of a second employer, "Other Co", and tries every way to
 * reach Acme's: its lists, its forms, its pickers, and a job of its own. Each
 * must behave as if Acme's rows did not exist - 404, or an error that says
 * nothing about them. Every other screen test runs as the admin, who sees
 * everything and so could not notice a leak.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles(resolver = TestProfiles.class)
@Transactional
class LocationTagScopeTest {

    /** Fixed ids from the seed: Acme, its Bern location, its "java" tag. */
    private static final UUID ACME = UUID.fromString("003d6aec-021b-11f1-aefa-f649a5d91690");
    private static final String BERN = "2c2e59d5-0b1a-11f1-938c-42a3421a666f";
    private static final long JAVA = 1L;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private EmployerService employerService;
    @Autowired
    private EmployerRepo employerRepo;
    @Autowired
    private UserRepo userRepo;
    @Autowired
    private LocationRepo locationRepo;
    @Autowired
    private TagRepo tagRepo;
    @Autowired
    private TagService tagService;
    @Autowired
    private JobRepo jobRepo;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EntityManager entityManager;

    @MockitoBean
    private CurrentUserService currentUserService;

    private Employer other;
    private Actor owner;

    @BeforeEach
    void actAsTheOwnerOfAnotherEmployer() {
        UserEntity user = new UserEntity();
        user.setIssuer("test");
        user.setSubject("other-owner-" + UUID.randomUUID());
        user.setDisplayName("Olivia Other");
        user.setRole(UserEntity.Role.USER);
        user = userRepo.save(user);

        Actor creating = Actor.user(user.getId(), "Olivia Other", null, false, Map.of());
        other = employerService.save(null, "Other Co", null, null, null,
                Headquarters.newLocation("Lausanne", "CH"), creating);

        owner = Actor.user(user.getId(), "Olivia Other", null, false,
                Map.of(other.getId(), MembershipRole.OWNER));
        given(currentUserService.isDevMode()).willReturn(true);
        given(currentUserService.current()).willReturn(owner);
    }

    private String page(String path) throws Exception {
        return mvc.perform(get(path)).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    // ------------------------------------------------------- the headquarters

    /**
     * A new employer's headquarters is entered on its form and becomes its first
     * location - its own, not a shared row (spec 3.1).
     */
    @Test
    void aNewEmployersHeadquartersBecomesItsOwnLocation() {
        Location hq = employerRepo.findById(other.getId()).orElseThrow().getHeadquarters();
        assertThat(hq.getCity()).isEqualTo("Lausanne");
        assertThat(hq.getEmployer().getId()).isEqualTo(other.getId());
    }

    /** Its edit form offers only its own locations to pick from. */
    @Test
    void theHeadquartersSelectOffersOnlyOwnLocations() throws Exception {
        String form = page("/employers/" + other.getId() + "/update");
        assertThat(form).contains("Lausanne").doesNotContain("Zürich").doesNotContain(BERN);
    }

    /** Another employer's location cannot become its headquarters, even by id. */
    @Test
    void anotherEmployersLocationCannotBeItsHeadquarters() throws Exception {
        String body = mvc.perform(post("/employers/" + other.getId() + "/update")
                        .param("name", "Other Co").param("headquarters", BERN))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("Choose one of this employer&#39;s locations");
        assertThat(employerRepo.findById(other.getId()).orElseThrow().getHeadquarters().getCity())
                .isEqualTo("Lausanne");
    }

    // -------------------------------------------------------------- the lists

    @Test
    void theListsShowOnlyItsOwn() throws Exception {
        assertThat(page("/locations")).contains("Lausanne").doesNotContain("Zürich").doesNotContain("Berlin");
        tagService.create(other, "cobol", owner);
        assertThat(page("/tags")).contains("cobol").doesNotContain(">java<").doesNotContain("kubernetes");
    }

    /** Acme's rows are not there to edit or delete: 404, never 403 (spec 2.4). */
    @Test
    void anotherEmployersLocationAndTagAreNotFound() throws Exception {
        mvc.perform(get("/locations/" + BERN + "/update")).andExpect(status().isNotFound());
        mvc.perform(post("/locations/" + BERN + "/update").param("city", "Mine").param("country", "CH"))
                .andExpect(status().isNotFound());
        mvc.perform(post("/locations/" + BERN + "/delete")).andExpect(status().isNotFound());
        mvc.perform(get("/tags/" + JAVA + "/update")).andExpect(status().isNotFound());
        mvc.perform(post("/tags/" + JAVA + "/update").param("name", "mine")).andExpect(status().isNotFound());
        mvc.perform(post("/tags/" + JAVA + "/delete")).andExpect(status().isNotFound());

        assertThat(locationRepo.findById(UUID.fromString(BERN)).orElseThrow().getCity()).isEqualTo("Bern");
        assertThat(tagRepo.findById(JAVA).orElseThrow().getName()).isEqualTo("java");
    }

    /** A new location or tag belongs to the employer being worked on. */
    @Test
    void createdRowsBelongToTheActiveEmployer() throws Exception {
        mvc.perform(post("/locations/create").param("city", "Genève").param("country", "CH"))
                .andExpect(status().is3xxRedirection());
        mvc.perform(post("/tags/create").param("name", "fortran")).andExpect(status().is3xxRedirection());

        assertThat(locationRepo.findByEmployerIdOrderByCityAsc(other.getId()))
                .extracting(Location::getCity).contains("Genève");
        assertThat(tagRepo.findFirstByEmployerIdAndNameIgnoreCase(other.getId(), "fortran")).isPresent();
        assertThat(tagRepo.findFirstByEmployerIdAndNameIgnoreCase(ACME, "fortran")).isEmpty();
    }

    // ----------------------------------------------------------------- a job

    /** The pickers search the job's employer, and nobody may search someone else's. */
    @Test
    void thePickersSearchOnlyTheJobsEmployer() throws Exception {
        assertThat(page("/jobs/locations/search?employer=" + other.getId() + "&q="))
                .contains("Lausanne").doesNotContain("Bern");
        mvc.perform(get("/jobs/locations/search?employer=" + ACME + "&q=ber"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/jobs/tags/search?employer=" + ACME + "&q=ja"))
                .andExpect(status().isNotFound());
    }

    /**
     * Posting Acme's ids straight at the form is refused, and the re-rendered form
     * does not bring Acme's names back with it (spec 3.3).
     */
    @Test
    void aJobCannotUseAnotherEmployersLocationOrTag() throws Exception {
        long before = jobRepo.count();
        String body = mvc.perform(post("/jobs/create")
                        .param("title", "Stolen goods").param("url", "https://other.example/job")
                        .param("language", "en").param("jobType", "permanent")
                        .param("locations", BERN).param("tags", String.valueOf(JAVA)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(body).contains("Choose one of this employer&#39;s");
        assertThat(body).doesNotContain(">Bern, CH<").doesNotContain(">java<");
        assertThat(jobRepo.count()).isEqualTo(before);
    }

    // ------------------------------------------------------------ deletion

    /** An employer's locations and tags go with it, whichever way the rows point (spec 3.2). */
    @Test
    void deletingAnEmployerTakesItsLocationsAndTags() {
        tagService.create(other, "cobol", owner);
        // Written first, then read back from the database rather than Hibernate's
        // cache - otherwise this would test the session, not the foreign keys.
        entityManager.flush();
        jdbc.update("DELETE FROM employers WHERE id = ?", other.getId().toString());
        entityManager.clear();
        assertThat(locationRepo.findByEmployerIdOrderByCityAsc(other.getId())).isEmpty();
        assertThat(tagRepo.findFirstByEmployerIdAndNameIgnoreCase(other.getId(), "cobol")).isEmpty();
        assertThat(locationRepo.findById(UUID.fromString(BERN))).as("Acme's are untouched").isPresent();
    }
}
