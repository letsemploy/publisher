package org.letsemploy.ojobpub_publisher.ojobpub.v1;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.neovisionaries.i18n.CountryCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.letsemploy.ojobpub_publisher.employer.Employer;
import org.letsemploy.ojobpub_publisher.feed.Feed;
import org.letsemploy.ojobpub_publisher.job.*;
import org.letsemploy.ojobpub_publisher.location.Location;
import org.letsemploy.ojobpub_publisher.ojobpub.v1.service.OjobpubService;
import org.letsemploy.ojobpub_publisher.ojobpub.v1.service.OjobpubValidator;
import org.letsemploy.ojobpub_publisher.tag.Tag;

/**
 * The build gate of specification section 10: a generated document must validate
 * against the checked-in oJobPub schema. Everything else in this project is
 * internal; this is the contract.
 *
 * <p>Plain unit test - no Spring context and no database.
 */
class OjobpubConformanceTest {

    private static OjobpubValidator validator;
    private static ObjectMapper mapper;
    private final OjobpubService service = new OjobpubService();

    @BeforeAll
    static void setUp() throws Exception {
        mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL);
        validator = new OjobpubValidator(mapper);
        validator.load();
    }

    private Location location(String city, CountryCode country) {
        return new Location(city, country);
    }

    private Employer employer() {
        Employer e = new Employer();
        e.setName("Acme AG");
        e.setSlug("acme-ag");
        e.setUrl("https://www.acme.example");
        e.setIndustry("Software");
        e.setHeadquarters(location("Bern", CountryCode.CH));
        e.setLastModifiedAt(Instant.parse("2026-09-21T08:15:00Z"));
        return e;
    }

    /** The minimum conforming posting: only the six required job fields. */
    private Job minimalJob(Employer employer) {
        Job job = new Job();
        // Persisted jobs always carry an id; assign one so exclusions can name them.
        job.setId(java.util.UUID.randomUUID());
        job.setEmployer(employer);
        job.setTitle("Praktikum Produktdesign");
        job.setUrl("https://www.acme.example/jobs/ACME-2026-021");
        job.setLanguageCode("de");
        job.setJobType(JobType.INTERNSHIP);
        job.setStatus(JobStatus.ACTIVE);
        job.setPublishedAt(LocalDate.of(2026, 9, 18));
        job.setLocations(Set.of(location("Bern", CountryCode.CH)));
        job.setLastModifiedAt(Instant.parse("2026-09-18T06:00:00Z"));
        return job;
    }

    /** Every optional field populated, to exercise the whole mapping. */
    private Job maximalJob(Employer employer) {
        Job job = minimalJob(employer);
        job.setTitle("Senior Backend Engineer");
        job.setDescription("Design and operate our job distribution platform.");
        job.setCategory("Engineering");
        job.setReferenceId("ACME-2026-014");
        job.setJobType(JobType.PERMANENT);
        job.setWorkType(WorkType.ON_SITE);
        job.setExperienceLevel(ExperienceLevel.DIRECTOR);
        job.setWorkLoadPercentMin(80);
        job.setWorkLoadPercentMax(100);
        job.setSalaryMin(new BigDecimal("110000.00"));
        job.setSalaryMax(new BigDecimal("135000.00"));
        job.setSalaryCurrency("chf");
        job.setSalaryInterval(SalaryInterval.YEARLY);
        job.setStartDate(LocalDate.of(2026, 11, 1));
        job.setApplyBefore(LocalDate.of(2099, 10, 15));
        job.setLanguageCode("EN");
        job.setUrl("https://www.acme.example/jobs/ACME-2026-014");
        job.setLocations(Set.of(location("Bern", CountryCode.CH), location("Zurich", CountryCode.CH)));
        job.setTags(Set.of(new Tag("java"), new Tag("kubernetes"), new Tag("spring")));
        return job;
    }

    private Feed feed(Employer employer, Job... jobs) {
        Feed feed = new Feed();
        feed.setEmployer(employer);
        feed.setName("All jobs");
        feed.setSlug("all");
        feed.setLastModifiedAt(Instant.parse("2026-09-21T08:15:00Z"));
        feed.setJobs(new java.util.LinkedHashSet<>(List.of(jobs)));
        return feed;
    }

    private void assertConforms(Feed feed) {
        var document = service.generate(feed, LocalDate.of(2026, 9, 21)).getDocument();
        assertThat(validator.validate(document)).as("schema violations").isEmpty();
    }

    /**
     * The gate must be able to fail. Every other test here asserts that a
     * document conforms, which a validator that accepted everything would
     * satisfy just as well - so one case has to prove the validator says no.
     *
     * <p>Worth keeping in view whenever the schema library is upgraded: its API
     * has been renamed wholesale once already, and a rewrite that quietly
     * validated nothing would otherwise be indistinguishable from a working one.
     */
    @Test
    void aDocumentMissingRequiredFieldsIsRejected() {
        assertThat(validator.validate(Map.of("version", "1.0")))
                .as("violations for a document with only one of the four required fields")
                .isNotEmpty();
    }

    /** The schema types `version` as a string; a number must not slip through. */
    @Test
    void aWronglyTypedFieldIsRejected() {
        assertThat(validator.validate(Map.of(
                "version", 1,
                "lastUpdated", "2026-09-21T08:15:00Z",
                "employer", Map.of("name", "Acme AG"),
                "jobs", List.of())))
                .as("violations for a numeric version")
                .isNotEmpty();
    }

    @Test
    void minimalDocumentConforms() {
        Employer employer = employer();
        assertConforms(feed(employer, minimalJob(employer)));
    }

    @Test
    void maximalDocumentConforms() {
        Employer employer = employer();
        assertConforms(feed(employer, maximalJob(employer)));
    }

    /** A feed with nothing to publish must still serve a valid document (spec 5.2). */
    @Test
    void emptyFeedConforms() {
        assertConforms(feed(employer()));
    }

    @Test
    void workTypeUsesTheHyphenatedFormTheSchemaRequires() {
        Employer employer = employer();
        var document = service.generate(feed(employer, maximalJob(employer)),
                LocalDate.of(2026, 9, 21)).getDocument();
        assertThat(document.getJobs().get(0).getWorkType()).isEqualTo("on-site");
    }

    /** A LocalDateTime would emit no offset and fail the date-time format. */
    @Test
    void lastUpdatedIsRfc3339WithOffset() throws Exception {
        Employer employer = employer();
        var document = service.generate(feed(employer, minimalJob(employer)),
                LocalDate.of(2026, 9, 21)).getDocument();
        assertThat(mapper.writeValueAsString(document))
                .containsPattern("\"lastUpdated\"\\s*:\\s*\"\\d{4}-\\d{2}-\\d{2}T[\\d:.]+Z\"");
    }

    @Test
    void codesAreNormalisedToTheCaseTheSchemaDocuments() {
        Employer employer = employer();
        var document = service.generate(feed(employer, maximalJob(employer)),
                LocalDate.of(2026, 9, 21)).getDocument();
        var job = document.getJobs().get(0);
        assertThat(document.getEmployer().getLocation().getCountry()).isEqualTo("CH");
        assertThat(job.getLocations()).allSatisfy(l -> assertThat(l.getCountry()).isEqualTo("CH"));
        assertThat(job.getSalary().getCurrency()).isEqualTo("CHF");
        assertThat(job.getLanguage()).isEqualTo("en");
    }

    /** Tags are part of the contract and the most useful signal in the document. */
    @Test
    void tagsArePublished() {
        Employer employer = employer();
        var document = service.generate(feed(employer, maximalJob(employer)),
                LocalDate.of(2026, 9, 21)).getDocument();
        assertThat(document.getJobs().get(0).getTags())
                .containsExactly("java", "kubernetes", "spring");
    }

    /** A salary with only one amount must not blow up or fabricate the other. */
    @Test
    void partialSalaryIsHandled() {
        Employer employer = employer();
        Job job = minimalJob(employer);
        job.setSalaryMin(new BigDecimal("90000"));
        job.setSalaryCurrency("EUR");
        job.setSalaryInterval(SalaryInterval.YEARLY);
        Feed feed = feed(employer, job);
        assertConforms(feed);
        var salary = service.generate(feed, LocalDate.of(2026, 9, 21))
                .getDocument().getJobs().get(0).getSalary();
        assertThat(salary.getMin()).isEqualByComparingTo("90000");
        assertThat(salary.getMax()).isNull();
    }

    /** Six-figure salaries must not lose accuracy (spec 6.7). */
    @Test
    void salaryKeepsItsPrecision() throws Exception {
        Employer employer = employer();
        Job job = minimalJob(employer);
        job.setSalaryMin(new BigDecimal("123456.78"));
        job.setSalaryCurrency("CHF");
        job.setSalaryInterval(SalaryInterval.YEARLY);
        assertThat(mapper.writeValueAsString(
                service.generate(feed(employer, job), LocalDate.of(2026, 9, 21)).getDocument()))
                .contains("123456.78");
    }

    /** Expired and draft postings leave the document without anyone editing them. */
    @Test
    void expiredAndUnreadyJobsAreExcludedAndExplained() {
        Employer employer = employer();
        Job expired = minimalJob(employer);
        expired.setTitle("Site Reliability Engineer");
        expired.setApplyBefore(LocalDate.of(2020, 1, 31));
        Job draft = minimalJob(employer);
        draft.setTitle("Werkstudent");
        draft.setStatus(JobStatus.DRAFT);

        var result = service.generate(feed(employer, expired, draft), LocalDate.of(2026, 9, 21));
        assertThat(result.getDocument().getJobs()).isEmpty();
        assertThat(result.getExclusions()).extracting(OjobpubService.Exclusion::getReasonKey)
                .containsExactlyInAnyOrder("feed.reason.expired", "feed.reason.draft");
        assertThat(validator.validate(result.getDocument())).isEmpty();
    }
}
