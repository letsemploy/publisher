package io.letsemploy.publisher.service;

import io.letsemploy.publisher.domain.*;
import io.letsemploy.publisher.repository.*;
import io.letsemploy.publisher.support.Slugifier;
import io.letsemploy.publisher.web.form.JobForm;
import jakarta.persistence.criteria.JoinType;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class JobService {

    private final JobRepository jobRepository;
    private final PublishingDomainRepository domainRepository;
    private final CategoryRepository categoryRepository;
    private final EmployerRepository employerRepository;
    private final LocationRepository locationRepository;
    private final TagRepository tagRepository;

    public JobService(JobRepository jobRepository,
                      PublishingDomainRepository domainRepository,
                      CategoryRepository categoryRepository,
                      EmployerRepository employerRepository,
                      LocationRepository locationRepository,
                      TagRepository tagRepository) {
        this.jobRepository = jobRepository;
        this.domainRepository = domainRepository;
        this.categoryRepository = categoryRepository;
        this.employerRepository = employerRepository;
        this.locationRepository = locationRepository;
        this.tagRepository = tagRepository;
    }

    public List<Job> search(JobFilters filters) {
        return jobRepository.findAll(specification(filters), org.springframework.data.domain.Sort.by(
                org.springframework.data.domain.Sort.Order.desc("publishedAt"),
                org.springframework.data.domain.Sort.Order.asc("title")));
    }

    @Transactional
    public Job save(Job job, JobForm form) {
        PublishingDomain domain = domainRepository.findById(form.domainId()).orElseThrow();
        job.setDomain(domain);
        job.setReferenceId(form.referenceId());
        job.setTitle(form.title());
        job.setDescription(form.description());
        job.setLanguage(form.language());
        job.setPublishedAt(form.publishedAt());
        job.setStartDate(form.startDate());
        job.setEndDate(form.endDate());
        job.setApplyBefore(form.applyBefore());
        job.setJobType(form.jobType());
        job.setExperienceLevel(form.experienceLevel());
        job.setWorkType(form.workType());
        job.setActive(form.active());
        job.setMinWorkLoad(form.minWorkLoad());
        job.setMaxWorkLoad(form.maxWorkLoad());
        job.setMinSalary(form.minSalary());
        job.setMaxSalary(form.maxSalary());
        job.setSalaryCurrency(form.salaryCurrency());
        job.setSalaryInterval(form.salaryInterval());
        job.setUrl(form.url());
        job.setEmployer(StringUtils.hasText(form.employerId()) ? employerRepository.findById(form.employerId()).orElse(null) : null);
        job.setCategory(resolveCategory(domain, form.categoryName()));
        job.setLocations(new LinkedHashSet<>(locationRepository.findAllById(form.locationIds())));
        job.setTags(resolveTags(domain, form.tagNames()));
        return jobRepository.save(job);
    }

    public List<Job> eligibleJobs(JobExport export) {
        LocalDate today = LocalDate.now();
        return export.getJobs().stream()
                .filter(Job::isActive)
                .filter(job -> !job.getPublishedAt().isAfter(today))
                .sorted((left, right) -> left.getPublishedAt().equals(right.getPublishedAt())
                        ? left.getTitle().compareToIgnoreCase(right.getTitle())
                        : right.getPublishedAt().compareTo(left.getPublishedAt()))
                .toList();
    }

    private Category resolveCategory(PublishingDomain domain, String categoryName) {
        if (!StringUtils.hasText(categoryName)) {
            return null;
        }
        String slug = Slugifier.slugify(categoryName);
        return categoryRepository.findByDomainAndSlug(domain, slug)
                .orElseGet(() -> {
                    Category category = new Category();
                    category.setDomain(domain);
                    category.setName(categoryName.trim());
                    category.setSlug(slug);
                    return categoryRepository.save(category);
                });
    }

    private Set<Tag> resolveTags(PublishingDomain domain, String tagNames) {
        if (!StringUtils.hasText(tagNames)) {
            return Set.of();
        }
        return Arrays.stream(tagNames.split(","))
                .map(String::trim)
                .filter(StringUtils::hasText)
                .map(name -> {
                    String slug = Slugifier.slugify(name);
                    return tagRepository.findByDomainAndSlug(domain, slug)
                            .orElseGet(() -> {
                                Tag tag = new Tag();
                                tag.setDomain(domain);
                                tag.setName(name);
                                tag.setSlug(slug);
                                return tagRepository.save(tag);
                            });
                })
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private Specification<Job> specification(JobFilters filters) {
        return (root, query, cb) -> {
            query.distinct(true);
            var predicates = new java.util.ArrayList<jakarta.persistence.criteria.Predicate>();
            if (StringUtils.hasText(filters.query())) {
                String like = "%" + filters.query().toLowerCase() + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), like),
                        cb.like(cb.lower(root.get("description")), like),
                        cb.like(cb.lower(root.get("referenceId")), like)
                ));
            }
            if (StringUtils.hasText(filters.domainId())) {
                predicates.add(cb.equal(root.get("domain").get("id"), filters.domainId()));
            }
            if (filters.active() != null) {
                predicates.add(cb.equal(root.get("active"), filters.active()));
            }
            if (filters.jobType() != null) {
                predicates.add(cb.equal(root.get("jobType"), filters.jobType()));
            }
            if (filters.experienceLevel() != null) {
                predicates.add(cb.equal(root.get("experienceLevel"), filters.experienceLevel()));
            }
            if (filters.workType() != null) {
                predicates.add(cb.equal(root.get("workType"), filters.workType()));
            }
            if (StringUtils.hasText(filters.categoryId())) {
                predicates.add(cb.equal(root.get("category").get("id"), filters.categoryId()));
            }
            if (StringUtils.hasText(filters.employerId())) {
                predicates.add(cb.equal(root.get("employer").get("id"), filters.employerId()));
            }
            if (StringUtils.hasText(filters.locationId())) {
                predicates.add(cb.equal(root.join("locations", JoinType.LEFT).get("id"), filters.locationId()));
            }
            if (StringUtils.hasText(filters.tagId())) {
                predicates.add(cb.equal(root.join("tags", JoinType.LEFT).get("id"), filters.tagId()));
            }
            return cb.and(predicates.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
    }
}
