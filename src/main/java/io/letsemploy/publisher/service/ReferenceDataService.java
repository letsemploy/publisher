package io.letsemploy.publisher.service;

import io.letsemploy.publisher.domain.PublishingDomain;
import io.letsemploy.publisher.repository.*;
import org.springframework.stereotype.Service;

@Service
public class ReferenceDataService {

    private final AppUserRepository userRepository;
    private final TenantRepository tenantRepository;
    private final PublishingDomainRepository domainRepository;
    private final LocationRepository locationRepository;
    private final CategoryRepository categoryRepository;
    private final EmployerRepository employerRepository;
    private final TagRepository tagRepository;
    private final JobRepository jobRepository;
    private final JobExportRepository exportRepository;

    public ReferenceDataService(AppUserRepository userRepository,
                                TenantRepository tenantRepository,
                                PublishingDomainRepository domainRepository,
                                LocationRepository locationRepository,
                                CategoryRepository categoryRepository,
                                EmployerRepository employerRepository,
                                TagRepository tagRepository,
                                JobRepository jobRepository,
                                JobExportRepository exportRepository) {
        this.userRepository = userRepository;
        this.tenantRepository = tenantRepository;
        this.domainRepository = domainRepository;
        this.locationRepository = locationRepository;
        this.categoryRepository = categoryRepository;
        this.employerRepository = employerRepository;
        this.tagRepository = tagRepository;
        this.jobRepository = jobRepository;
        this.exportRepository = exportRepository;
    }

    public AppUserRepository users() { return userRepository; }
    public TenantRepository tenants() { return tenantRepository; }
    public PublishingDomainRepository domains() { return domainRepository; }
    public LocationRepository locations() { return locationRepository; }
    public CategoryRepository categories() { return categoryRepository; }
    public EmployerRepository employers() { return employerRepository; }
    public TagRepository tags() { return tagRepository; }
    public JobRepository jobs() { return jobRepository; }
    public JobExportRepository exports() { return exportRepository; }

    public PublishingDomain requireDomain(String domainId) {
        return domainRepository.findById(domainId).orElseThrow();
    }
}
