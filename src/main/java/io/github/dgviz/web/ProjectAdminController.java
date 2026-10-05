package io.github.dgviz.web;

import io.github.dgviz.security.AccessControlService;
import io.github.dgviz.group.GroupService;
import io.github.dgviz.project.ProjectService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/projects")
public class ProjectAdminController {

    private final ProjectService projectService;
    private final GroupService groupService;
    private final AccessControlService accessControl;

    public ProjectAdminController(
            ProjectService projectService,
            GroupService groupService,
            AccessControlService accessControl
    ) {
        this.projectService = projectService;
        this.groupService = groupService;
        this.accessControl = accessControl;
    }

    @GetMapping
    public String list(Model model) {
        accessControl.requireProjectManage();
        var user = accessControl.currentUser();
        model.addAttribute("projects",
                accessControl.isAdmin(user) ? projectService.findAll() : accessControl.accessibleProjects());
        model.addAttribute("groups", accessControl.manageableGroups());
        model.addAttribute("adminView", accessControl.isAdmin(user));
        return "admin/projects";
    }

    @PostMapping
    public String create(
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Long groupId,
            RedirectAttributes ra
    ) {
        try {
            accessControl.requireProjectManage();
            accessControl.requireAssignableGroup(groupId);
            projectService.create(name, description, groupId);
            ra.addFlashAttribute("success", "Project created");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/projects";
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) Long groupId,
            RedirectAttributes ra
    ) {
        try {
            accessControl.requireMutateProject(id);
            accessControl.requireAssignableGroup(groupId);
            projectService.update(id, name, description, groupId);
            ra.addFlashAttribute("success", "Project updated");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/projects";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            accessControl.requireMutateProject(id);
            projectService.delete(id);
            ra.addFlashAttribute("success", "Project deleted");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/projects";
    }
}
