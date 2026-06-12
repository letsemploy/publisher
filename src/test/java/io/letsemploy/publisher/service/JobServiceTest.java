package io.letsemploy.publisher.service;

import io.letsemploy.publisher.domain.*;
import io.letsemploy.publisher.repository.*;
import io.letsemploy.publisher.web.form.JobForm;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class JobServiceTest {

    @Mock JobRepository jobRepository;
    @Mock PublishingDomainRepository domainRepository;
    @Mock CategoryRepository categoryRepository;
    @Mock EmployerRepository employerRepository;
    @Mock LocationRepository locationRepository;
    @Mock TagRepository tagRepository;

    @InjectMocks JobService jobService;

    @Test
    void saveCreatesMissingCategoryAndTagsFromFreeText() {
        PublishingDomain domain = new PublishingDomain();
        domain.setId("domain-1");
        domain.setName("Example Domain");

        Location location = new Location();
        location.setId("loc-1");
        location.setCity("Zurich");
        location.setCountry("CH");
        location.setDomain(domain);

        when(domainRepository.findById("domain-1")).thenReturn(Optional.of(domain));
        when(categoryRepository.findByDomainAndSlug(domain, "engineering")).thenReturn(Optional.empty());
        when(categoryRepository.save(any(Category.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(tagRepository.findByDomainAndSlug(domain, "java")).thenReturn(Optional.empty());
        when(tagRepository.findByDomainAndSlug(domain, "spring-boot")).thenReturn(Optional.empty());
        when(tagRepository.save(any(Tag.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(locationRepository.findAllById(List.of("loc-1"))).thenReturn(List.of(location));
        when(jobRepository.save(any(Job.class))).thenAnswer(invocation -> invocation.getArgument(0));

        JobForm form = new JobForm();
        form.setDomainId("domain-1");
        form.setTitle("Platform Engineer");
        form.setLanguage("en");
        form.setPublishedAt(LocalDate.now());
        form.setJobType(JobType.PERMANENT);
        form.setUrl("https://example.com/jobs/platform");
        form.setCategoryName("Engineering");
        form.setTagNames("Java, Spring Boot");
        form.setLocationIds(List.of("loc-1"));

        Job saved = jobService.save(new Job(), form);

        assertThat(saved.getCategory()).extracting(Category::getName).isEqualTo("Engineering");
        assertThat(saved.getTags()).extracting(Tag::getName).containsExactly("Java", "Spring Boot");
        assertThat(saved.getLocations()).hasSize(1);
    }

    @Test
    void eligibleJobsExcludeInactiveAndFuturePublishedJobs() {
        JobExport export = new JobExport();
        export.setJobs(Set.of(
                job("Visible", true, LocalDate.now().minusDays(1)),
                job("Future", true, LocalDate.now().plusDays(1)),
                job("Inactive", false, LocalDate.now().minusDays(1))
        ));

        assertThat(jobService.eligibleJobs(export)).extracting(Job::getTitle).containsExactly("Visible");
    }

    private Job job(String title, boolean active, LocalDate publishedAt) {
        Job job = new Job();
        job.setTitle(title);
        job.setActive(active);
        job.setPublishedAt(publishedAt);
        job.setLanguage("en");
        job.setJobType(JobType.PERMANENT);
        job.setUrl("https://example.com/jobs/" + title.toLowerCase());
        return job;
    }
}
