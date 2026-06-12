package io.letsemploy.publisher.service;

import io.letsemploy.publisher.domain.Job;
import io.letsemploy.publisher.domain.JobExport;
import io.letsemploy.publisher.domain.Location;
import io.letsemploy.publisher.repository.EmployerRepository;
import io.letsemploy.publisher.repository.JobExportRepository;
import io.letsemploy.publisher.repository.JobRepository;
import io.letsemploy.publisher.repository.PublishingDomainRepository;
import io.letsemploy.publisher.web.form.ExportForm;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

@Service
public class ExportService {

    private final JobExportRepository exportRepository;
    private final PublishingDomainRepository domainRepository;
    private final EmployerRepository employerRepository;
    private final JobRepository jobRepository;
    private final JobService jobService;

    public ExportService(JobExportRepository exportRepository,
                         PublishingDomainRepository domainRepository,
                         EmployerRepository employerRepository,
                         JobRepository jobRepository,
                         JobService jobService) {
        this.exportRepository = exportRepository;
        this.domainRepository = domainRepository;
        this.employerRepository = employerRepository;
        this.jobRepository = jobRepository;
        this.jobService = jobService;
    }

    @Transactional
    public JobExport save(JobExport export, ExportForm form) {
        export.setDomain(domainRepository.findById(form.domainId()).orElseThrow());
        export.setName(form.name());
        export.setDescription(form.description());
        export.setActive(form.active());
        export.setEmployer(StringUtils.hasText(form.employerId()) ? employerRepository.findById(form.employerId()).orElse(null) : null);
        export.setJobs(new LinkedHashSet<>(jobRepository.findAllById(form.jobIds())));
        JobExport saved = exportRepository.save(export);
        if (saved.isActive()) {
            exportRepository.findAllByDomainAndIdNot(saved.getDomain(), saved.getId()).forEach(other -> {
                if (other.isActive()) {
                    other.setActive(false);
                    exportRepository.save(other);
                }
            });
        }
        return saved;
    }

    public ExportPayload buildPayload(JobExport export) {
        var employer = export.getEmployer();
        return new ExportPayload(
                "1.0",
                OffsetDateTime.now(ZoneOffset.UTC),
                employer == null ? null : new ExportPayload.EmployerPayload(
                        employer.getName(),
                        toLocation(employer.getHeadquarters()),
                        employer.getIndustry(),
                        employer.getUrl()),
                jobService.eligibleJobs(export).stream().map(this::toJob).toList()
        );
    }

    public JobExport activeExport(String domainSlug) {
        return exportRepository.findByDomainSlugAndActiveTrue(domainSlug).orElseThrow();
    }

    private ExportPayload.JobPayload toJob(Job job) {
        return new ExportPayload.JobPayload(
                job.getLanguage(),
                job.getPublishedAt(),
                job.getStartDate(),
                job.getEndDate(),
                job.getApplyBefore(),
                job.getCategory() == null ? null : job.getCategory().getName(),
                job.getReferenceId(),
                job.getTitle(),
                job.getDescription(),
                lowercase(job.getJobType()),
                lowercase(job.getExperienceLevel()),
                job.getMinWorkLoad() == null && job.getMaxWorkLoad() == null ? null : new ExportPayload.WorkLoadPayload(job.getMinWorkLoad(), job.getMaxWorkLoad()),
                lowercase(job.getWorkType()).replace('_', '-'),
                job.getMinSalary() == null && job.getMaxSalary() == null && !StringUtils.hasText(job.getSalaryCurrency()) && job.getSalaryInterval() == null ? null :
                        new ExportPayload.SalaryPayload(job.getMinSalary(), job.getMaxSalary(), job.getSalaryCurrency(), lowercase(job.getSalaryInterval())),
                job.getTags().stream().map(io.letsemploy.publisher.domain.Tag::getName).toList(),
                job.getLocations().stream().map(this::toLocation).toList(),
                job.getUrl()
        );
    }

    private ExportPayload.LocationPayload toLocation(Location location) {
        if (location == null) {
            return null;
        }
        return new ExportPayload.LocationPayload(location.getCity(), location.getCountry());
    }

    private String lowercase(Enum<?> value) {
        return value == null ? null : value.name().toLowerCase(Locale.ROOT);
    }
}
