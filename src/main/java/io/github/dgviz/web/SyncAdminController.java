package io.github.dgviz.web;

import io.github.dgviz.sync.SyncConfigService;
import io.github.dgviz.vulnerability.VulnerabilityRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/sync")
public class SyncAdminController {

    private final SyncConfigService syncConfigService;
    private final VulnerabilityRepository vulnerabilityRepository;

    public SyncAdminController(SyncConfigService syncConfigService, VulnerabilityRepository vulnerabilityRepository) {
        this.syncConfigService = syncConfigService;
        this.vulnerabilityRepository = vulnerabilityRepository;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("sources", syncConfigService.findAll());
        model.addAttribute("runs", syncConfigService.recentRuns());
        model.addAttribute("vulnCount", vulnerabilityRepository.count());
        model.addAttribute("nvdCount", vulnerabilityRepository.countBySource("NVD"));
        model.addAttribute("osvCount", vulnerabilityRepository.countBySource("OSV"));
        model.addAttribute("ghsaCount", vulnerabilityRepository.countBySource("GHSA"));
        model.addAttribute("snykCount", vulnerabilityRepository.countBySource("SNYK"));
        return "admin/sync";
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean enabled,
            @RequestParam String cronExpression,
            @RequestParam(required = false) String apiBaseUrl,
            RedirectAttributes ra
    ) {
        try {
            syncConfigService.update(id, enabled, cronExpression, apiBaseUrl);
            ra.addFlashAttribute("success", "Sync source updated and schedule reloaded");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/sync";
    }

    @PostMapping("/{id}/run")
    public String runNow(@PathVariable Long id, RedirectAttributes ra) {
        syncConfigService.triggerAsync(id);
        ra.addFlashAttribute("success", "Sync started asynchronously");
        return "redirect:/admin/sync";
    }
}
