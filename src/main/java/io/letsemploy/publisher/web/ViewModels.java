package io.letsemploy.publisher.web;

import io.letsemploy.publisher.domain.*;
import io.letsemploy.publisher.service.ReferenceDataService;
import org.springframework.ui.Model;

import java.util.List;

final class ViewModels {

    private ViewModels() {
    }

    static void populateCommon(Model model, ReferenceDataService referenceDataService) {
        model.addAttribute("tenants", referenceDataService.tenants().findAll());
        model.addAttribute("domains", referenceDataService.domains().findAll());
        model.addAttribute("users", referenceDataService.users().findAll());
        model.addAttribute("categories", referenceDataService.categories().findAll());
        model.addAttribute("employers", referenceDataService.employers().findAll());
        model.addAttribute("locations", referenceDataService.locations().findAll());
        model.addAttribute("tags", referenceDataService.tags().findAll());
        model.addAttribute("jobs", referenceDataService.jobs().findAll());
        model.addAttribute("jobTypes", JobType.values());
        model.addAttribute("experienceLevels", ExperienceLevel.values());
        model.addAttribute("workTypes", WorkType.values());
        model.addAttribute("salaryIntervals", SalaryInterval.values());
    }

    static List<String> ids(List<? extends BaseEntity> entities) {
        return entities.stream().map(BaseEntity::getId).toList();
    }
}
