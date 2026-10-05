package io.github.dgviz.web;

import io.github.dgviz.settings.AppSettingsService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/settings")
public class SettingsAdminController {

    private final AppSettingsService settingsService;

    public SettingsAdminController(AppSettingsService settingsService) {
        this.settingsService = settingsService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("settings", settingsService.findAll());
        return "admin/settings";
    }

    @PostMapping
    public String create(
            @RequestParam String key,
            @RequestParam(required = false) String value,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = "false") boolean secret,
            RedirectAttributes ra
    ) {
        try {
            settingsService.create(key, value, description, secret);
            ra.addFlashAttribute("success", "Setting created");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/settings";
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable Long id,
            @RequestParam(required = false) String value,
            @RequestParam(required = false) String description,
            @RequestParam(defaultValue = "false") boolean secret,
            RedirectAttributes ra
    ) {
        try {
            settingsService.update(id, value, description, secret);
            ra.addFlashAttribute("success", "Setting updated");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/settings";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        settingsService.delete(id);
        ra.addFlashAttribute("success", "Setting deleted");
        return "redirect:/admin/settings";
    }
}
