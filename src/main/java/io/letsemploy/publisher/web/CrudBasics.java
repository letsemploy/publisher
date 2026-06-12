package io.letsemploy.publisher.web;

import io.letsemploy.publisher.domain.*;
import io.letsemploy.publisher.repository.*;
import io.letsemploy.publisher.service.ExportPayload;
import io.letsemploy.publisher.service.ExportService;
import io.letsemploy.publisher.service.JobFilters;
import io.letsemploy.publisher.service.JobService;
import io.letsemploy.publisher.service.ReferenceDataService;
import io.letsemploy.publisher.web.form.*;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.LinkedHashSet;

@Controller
class UserController {
    private final ReferenceDataService ref;
    UserController(ReferenceDataService ref) { this.ref = ref; }
    @GetMapping("/users") String list(Model m){ m.addAttribute("items", ref.users().findAll()); return "users/list"; }
    @GetMapping("/users/new") String createForm(Model m){ ViewModels.populateCommon(m, ref); m.addAttribute("form", new UserForm()); return "users/form"; }
    @PostMapping("/users") String create(@ModelAttribute UserForm form){ var u = new AppUser(); bind(u, form); ref.users().save(u); return "redirect:/users"; }
    @GetMapping("/users/{id}") String view(@PathVariable String id, Model m){ ViewModels.populateCommon(m, ref); var u=ref.users().findById(id).orElseThrow(); var form=new UserForm(); form.setDisplayName(u.getDisplayName()); form.setExternalSubject(u.getExternalSubject()); form.setEmail(u.getEmail()); form.setTenantIds(u.getTenants().stream().map(BaseEntity::getId).toList()); m.addAttribute("item", u); m.addAttribute("form", form); return "users/detail"; }
    @PostMapping("/users/{id}") String update(@PathVariable String id, @ModelAttribute UserForm form){ var u=ref.users().findById(id).orElseThrow(); bind(u, form); ref.users().save(u); return "redirect:/users/"+id; }
    @PostMapping("/users/{id}/delete") String delete(@PathVariable String id){ ref.users().deleteById(id); return "redirect:/users"; }
    private void bind(AppUser u, UserForm form){ u.setDisplayName(form.getDisplayName()); u.setExternalSubject(form.getExternalSubject()); u.setEmail(form.getEmail()); u.setTenants(new LinkedHashSet<>(ref.tenants().findAllById(form.getTenantIds()))); }
}

@Controller
class TenantController {
    private final ReferenceDataService ref;
    TenantController(ReferenceDataService ref){this.ref=ref;}
    @GetMapping("/tenants") String list(Model m){m.addAttribute("items", ref.tenants().findAll()); return "tenants/list";}
    @GetMapping("/tenants/new") String form(Model m){ViewModels.populateCommon(m, ref); m.addAttribute("form", new TenantForm()); return "tenants/form";}
    @PostMapping("/tenants") String create(@ModelAttribute TenantForm form){ var t = new Tenant(); bind(t, form); ref.tenants().save(t); return "redirect:/tenants"; }
    @GetMapping("/tenants/{id}") String detail(@PathVariable String id, Model m){ ViewModels.populateCommon(m, ref); var t=ref.tenants().findById(id).orElseThrow(); var form=new TenantForm(); form.setName(t.getName()); form.setSlug(t.getSlug()); form.setDescription(t.getDescription()); form.setUserIds(t.getUsers().stream().map(BaseEntity::getId).toList()); m.addAttribute("item", t); m.addAttribute("form", form); return "tenants/detail"; }
    @PostMapping("/tenants/{id}") String update(@PathVariable String id, @ModelAttribute TenantForm form){ var t=ref.tenants().findById(id).orElseThrow(); bind(t, form); ref.tenants().save(t); return "redirect:/tenants/"+id; }
    @PostMapping("/tenants/{id}/delete") String delete(@PathVariable String id){ ref.tenants().deleteById(id); return "redirect:/tenants"; }
    private void bind(Tenant t, TenantForm form){ t.setName(form.getName()); t.setSlug(form.getSlug()); t.setDescription(form.getDescription()); t.setUsers(new LinkedHashSet<>(ref.users().findAllById(form.getUserIds()))); }
}

@Controller
class DomainController {
    private final ReferenceDataService ref;
    DomainController(ReferenceDataService ref){this.ref=ref;}
    @GetMapping("/domains") String list(Model m){m.addAttribute("items", ref.domains().findAll()); return "domains/list";}
    @GetMapping("/domains/new") String form(Model m){ViewModels.populateCommon(m, ref); m.addAttribute("form", new DomainForm()); return "domains/form";}
    @PostMapping("/domains") String create(@ModelAttribute DomainForm form){ var d = new PublishingDomain(); bind(d, form); ref.domains().save(d); return "redirect:/domains"; }
    @GetMapping("/domains/{id}") String detail(@PathVariable String id, Model m){ ViewModels.populateCommon(m, ref); var d=ref.domains().findById(id).orElseThrow(); var form=new DomainForm(); form.setTenantId(d.getTenant().getId()); form.setName(d.getName()); form.setSlug(d.getSlug()); form.setDescription(d.getDescription()); m.addAttribute("item", d); m.addAttribute("form", form); return "domains/detail"; }
    @PostMapping("/domains/{id}") String update(@PathVariable String id, @ModelAttribute DomainForm form){ var d=ref.domains().findById(id).orElseThrow(); bind(d, form); ref.domains().save(d); return "redirect:/domains/"+id; }
    @PostMapping("/domains/{id}/delete") String delete(@PathVariable String id){ ref.domains().deleteById(id); return "redirect:/domains"; }
    private void bind(PublishingDomain d, DomainForm form){ d.setTenant(ref.tenants().findById(form.getTenantId()).orElseThrow()); d.setName(form.getName()); d.setSlug(form.getSlug()); d.setDescription(form.getDescription()); }
}

@Controller
class LocationController {
    private final ReferenceDataService ref;
    LocationController(ReferenceDataService ref){this.ref=ref;}
    @GetMapping("/locations") String list(Model m){m.addAttribute("items", ref.locations().findAll()); return "locations/list";}
    @GetMapping("/locations/new") String form(Model m){ViewModels.populateCommon(m, ref); m.addAttribute("form", new LocationForm()); return "locations/form";}
    @PostMapping("/locations") String create(@ModelAttribute LocationForm form){ var e = new Location(); bind(e, form); ref.locations().save(e); return "redirect:/locations"; }
    @GetMapping("/locations/{id}") String detail(@PathVariable String id, Model m){ ViewModels.populateCommon(m, ref); var e=ref.locations().findById(id).orElseThrow(); var form=new LocationForm(); form.setDomainId(e.getDomain().getId()); form.setCity(e.getCity()); form.setCountry(e.getCountry()); m.addAttribute("item", e); m.addAttribute("form", form); return "locations/detail"; }
    @PostMapping("/locations/{id}") String update(@PathVariable String id, @ModelAttribute LocationForm form){ var e=ref.locations().findById(id).orElseThrow(); bind(e, form); ref.locations().save(e); return "redirect:/locations/"+id; }
    @PostMapping("/locations/{id}/delete") String delete(@PathVariable String id){ ref.locations().deleteById(id); return "redirect:/locations"; }
    private void bind(Location e, LocationForm form){ e.setDomain(ref.domains().findById(form.getDomainId()).orElseThrow()); e.setCity(form.getCity()); e.setCountry(form.getCountry()); }
}

@Controller
class CategoryController {
    private final ReferenceDataService ref;
    CategoryController(ReferenceDataService ref){this.ref=ref;}
    @GetMapping("/categories") String list(Model m){m.addAttribute("items", ref.categories().findAll()); return "categories/list";}
    @GetMapping("/categories/new") String form(Model m){ViewModels.populateCommon(m, ref); m.addAttribute("form", new CategoryForm()); return "categories/form";}
    @PostMapping("/categories") String create(@ModelAttribute CategoryForm form){ var e = new Category(); bind(e, form); ref.categories().save(e); return "redirect:/categories"; }
    @GetMapping("/categories/{id}") String detail(@PathVariable String id, Model m){ ViewModels.populateCommon(m, ref); var e=ref.categories().findById(id).orElseThrow(); var form=new CategoryForm(); form.setDomainId(e.getDomain().getId()); form.setName(e.getName()); form.setSlug(e.getSlug()); form.setDescription(e.getDescription()); m.addAttribute("item", e); m.addAttribute("form", form); return "categories/detail"; }
    @PostMapping("/categories/{id}") String update(@PathVariable String id, @ModelAttribute CategoryForm form){ var e=ref.categories().findById(id).orElseThrow(); bind(e, form); ref.categories().save(e); return "redirect:/categories/"+id; }
    @PostMapping("/categories/{id}/delete") String delete(@PathVariable String id){ ref.categories().deleteById(id); return "redirect:/categories"; }
    private void bind(Category e, CategoryForm form){ e.setDomain(ref.domains().findById(form.getDomainId()).orElseThrow()); e.setName(form.getName()); e.setSlug(form.getSlug()); e.setDescription(form.getDescription()); }
}

@Controller
class EmployerController {
    private final ReferenceDataService ref;
    EmployerController(ReferenceDataService ref){this.ref=ref;}
    @GetMapping("/employers") String list(Model m){m.addAttribute("items", ref.employers().findAll()); return "employers/list";}
    @GetMapping("/employers/new") String form(Model m){ViewModels.populateCommon(m, ref); m.addAttribute("form", new EmployerForm()); return "employers/form";}
    @PostMapping("/employers") String create(@ModelAttribute EmployerForm form){ var e = new Employer(); bind(e, form); ref.employers().save(e); return "redirect:/employers"; }
    @GetMapping("/employers/{id}") String detail(@PathVariable String id, Model m){ ViewModels.populateCommon(m, ref); var e=ref.employers().findById(id).orElseThrow(); var form=new EmployerForm(); form.setDomainId(e.getDomain().getId()); form.setHeadquartersId(e.getHeadquarters()==null?null:e.getHeadquarters().getId()); form.setName(e.getName()); form.setSlug(e.getSlug()); form.setDescription(e.getDescription()); form.setIndustry(e.getIndustry()); form.setUrl(e.getUrl()); m.addAttribute("item", e); m.addAttribute("form", form); return "employers/detail"; }
    @PostMapping("/employers/{id}") String update(@PathVariable String id, @ModelAttribute EmployerForm form){ var e=ref.employers().findById(id).orElseThrow(); bind(e, form); ref.employers().save(e); return "redirect:/employers/"+id; }
    @PostMapping("/employers/{id}/delete") String delete(@PathVariable String id){ ref.employers().deleteById(id); return "redirect:/employers"; }
    private void bind(Employer e, EmployerForm form){ e.setDomain(ref.domains().findById(form.getDomainId()).orElseThrow()); e.setHeadquarters(form.getHeadquartersId()==null||form.getHeadquartersId().isBlank()?null:ref.locations().findById(form.getHeadquartersId()).orElse(null)); e.setName(form.getName()); e.setSlug(form.getSlug()); e.setDescription(form.getDescription()); e.setIndustry(form.getIndustry()); e.setUrl(form.getUrl()); }
}

@Controller
class TagController {
    private final ReferenceDataService ref;
    TagController(ReferenceDataService ref){this.ref=ref;}
    @GetMapping("/tags") String list(Model m){m.addAttribute("items", ref.tags().findAll()); return "tags/list";}
    @GetMapping("/tags/new") String form(Model m){ViewModels.populateCommon(m, ref); m.addAttribute("form", new TagForm()); return "tags/form";}
    @PostMapping("/tags") String create(@ModelAttribute TagForm form){ var e = new Tag(); bind(e, form); ref.tags().save(e); return "redirect:/tags"; }
    @GetMapping("/tags/{id}") String detail(@PathVariable String id, Model m){ ViewModels.populateCommon(m, ref); var e=ref.tags().findById(id).orElseThrow(); var form=new TagForm(); form.setDomainId(e.getDomain().getId()); form.setName(e.getName()); form.setSlug(e.getSlug()); form.setDescription(e.getDescription()); m.addAttribute("item", e); m.addAttribute("form", form); return "tags/detail"; }
    @PostMapping("/tags/{id}") String update(@PathVariable String id, @ModelAttribute TagForm form){ var e=ref.tags().findById(id).orElseThrow(); bind(e, form); ref.tags().save(e); return "redirect:/tags/"+id; }
    @PostMapping("/tags/{id}/delete") String delete(@PathVariable String id){ ref.tags().deleteById(id); return "redirect:/tags"; }
    private void bind(Tag e, TagForm form){ e.setDomain(ref.domains().findById(form.getDomainId()).orElseThrow()); e.setName(form.getName()); e.setSlug(form.getSlug()); e.setDescription(form.getDescription()); }
}

@Controller
class JobController {
    private final ReferenceDataService ref; private final JobService jobs;
    JobController(ReferenceDataService ref, JobService jobs){this.ref=ref;this.jobs=jobs;}
    @GetMapping("/jobs") String list(@RequestParam(required = false) String query,@RequestParam(required = false) String domainId,@RequestParam(required = false) Boolean active,@RequestParam(required = false) JobType jobType,@RequestParam(required = false) ExperienceLevel experienceLevel,@RequestParam(required = false) WorkType workType,@RequestParam(required = false) String categoryId,@RequestParam(required = false) String employerId,@RequestParam(required = false) String locationId,@RequestParam(required = false) String tagId, Model m){ ViewModels.populateCommon(m, ref); m.addAttribute("items", jobs.search(new JobFilters(query,domainId,active,jobType,experienceLevel,workType,categoryId,employerId,locationId,tagId))); return "jobs/list";}
    @GetMapping("/jobs/results") String results(@RequestParam(required = false) String query,@RequestParam(required = false) String domainId,@RequestParam(required = false) Boolean active,@RequestParam(required = false) JobType jobType,@RequestParam(required = false) ExperienceLevel experienceLevel,@RequestParam(required = false) WorkType workType,@RequestParam(required = false) String categoryId,@RequestParam(required = false) String employerId,@RequestParam(required = false) String locationId,@RequestParam(required = false) String tagId, Model m){ m.addAttribute("items", jobs.search(new JobFilters(query,domainId,active,jobType,experienceLevel,workType,categoryId,employerId,locationId,tagId))); return "jobs/results";}
    @GetMapping("/jobs/new") String form(Model m){ViewModels.populateCommon(m, ref); m.addAttribute("form", new JobForm()); return "jobs/form";}
    @PostMapping("/jobs") String create(@ModelAttribute JobForm form){ var j = new Job(); jobs.save(j, form); return "redirect:/jobs"; }
    @GetMapping("/jobs/{id}") @Transactional String detail(@PathVariable String id, Model m){ ViewModels.populateCommon(m, ref); var j=ref.jobs().findById(id).orElseThrow(); m.addAttribute("item", j); m.addAttribute("form", JobForm.from(j)); return "jobs/detail"; }
    @PostMapping("/jobs/{id}") String update(@PathVariable String id, @ModelAttribute JobForm form){ var j=ref.jobs().findById(id).orElseThrow(); jobs.save(j, form); return "redirect:/jobs/"+id; }
    @PostMapping("/jobs/{id}/delete") String delete(@PathVariable String id){ ref.jobs().deleteById(id); return "redirect:/jobs"; }
}

@Controller
class ExportController {
    private final ReferenceDataService ref; private final ExportService exports;
    ExportController(ReferenceDataService ref, ExportService exports){this.ref=ref;this.exports=exports;}
    @GetMapping("/exports") String list(Model m){m.addAttribute("items", ref.exports().findAll()); return "exports/list";}
    @GetMapping("/exports/new") String form(Model m){ViewModels.populateCommon(m, ref); m.addAttribute("form", new ExportForm()); return "exports/form";}
    @PostMapping("/exports") String create(@ModelAttribute ExportForm form){ var e = new JobExport(); exports.save(e, form); return "redirect:/exports"; }
    @GetMapping("/exports/{id}") @Transactional String detail(@PathVariable String id, Model m){ ViewModels.populateCommon(m, ref); var e=ref.exports().findById(id).orElseThrow(); m.addAttribute("item", e); m.addAttribute("form", ExportForm.from(e)); m.addAttribute("payload", exports.buildPayload(e)); m.addAttribute("publicUrl", "/public/domains/"+e.getDomain().getSlug()+"/ojobpub.json"); return "exports/detail"; }
    @PostMapping("/exports/{id}") String update(@PathVariable String id, @ModelAttribute ExportForm form){ var e=ref.exports().findById(id).orElseThrow(); exports.save(e, form); return "redirect:/exports/"+id; }
    @PostMapping("/exports/{id}/delete") String delete(@PathVariable String id){ ref.exports().deleteById(id); return "redirect:/exports"; }
}

@RestController
class PublicExportController {
    private final ExportService exports;
    PublicExportController(ExportService exports){this.exports=exports;}
    @GetMapping(value = "/public/domains/{domainSlug}/ojobpub.json", produces = "application/json")
    ExportPayload publicExport(@PathVariable String domainSlug){ return exports.buildPayload(exports.activeExport(domainSlug)); }
}
