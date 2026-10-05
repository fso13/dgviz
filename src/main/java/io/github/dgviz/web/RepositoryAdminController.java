package io.github.dgviz.web;

import io.github.dgviz.project.ProjectService;
import io.github.dgviz.repository.RepositoryService;
import io.github.dgviz.repository.RepositorySourceType;
import io.github.dgviz.security.AccessControlService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/repositories")
public class RepositoryAdminController {

    private final RepositoryService repositoryService;
    private final ProjectService projectService;
    private final AccessControlService accessControl;

    public RepositoryAdminController(
            RepositoryService repositoryService,
            ProjectService projectService,
            AccessControlService accessControl
    ) {
        this.repositoryService = repositoryService;
        this.projectService = projectService;
        this.accessControl = accessControl;
    }

    @GetMapping
    public String list(Model model) {
        accessControl.requireRepositoryManage();
        var user = accessControl.currentUser();
        model.addAttribute("repositories",
                accessControl.isAdmin(user)
                        ? repositoryService.findAll()
                        : accessControl.accessibleRepositories());
        model.addAttribute("projects", accessControl.accessibleProjects());
        model.addAttribute("sourceTypes", new RepositorySourceType[]{
                RepositorySourceType.PLUGIN,
                RepositorySourceType.GITLAB,
                RepositorySourceType.GITHUB
        });
        return "admin/repositories";
    }

    @PostMapping
    public String create(
            @RequestParam Long projectId,
            @RequestParam String name,
            @RequestParam(defaultValue = "PLUGIN") String sourceType,
            @RequestParam(required = false) String vcsUrl,
            @RequestParam(required = false) String gitlabPath,
            @RequestParam(required = false) String remoteHost,
            @RequestParam(required = false) String accessToken,
            @RequestParam(defaultValue = "main") String defaultBranch,
            @RequestParam(defaultValue = "gradle") String buildSystem,
            @RequestParam(defaultValue = "false") boolean createIssuesDefault,
            RedirectAttributes ra
    ) {
        try {
            accessControl.requireCreateRepositoryIn(projectId);
            var repo = repositoryService.create(
                    projectId, name, sourceType, vcsUrl, gitlabPath, remoteHost,
                    accessToken, defaultBranch, buildSystem, createIssuesDefault);
            ra.addFlashAttribute("success",
                    "Repository #" + repo.getId() + " created. Scan token: " + repo.getScanToken());
            ra.addFlashAttribute("createdRepoId", repo.getId());
            ra.addFlashAttribute("createdScanToken", repo.getScanToken());
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/repositories";
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable Long id,
            @RequestParam Long projectId,
            @RequestParam String name,
            @RequestParam(defaultValue = "PLUGIN") String sourceType,
            @RequestParam(required = false) String vcsUrl,
            @RequestParam(required = false) String gitlabPath,
            @RequestParam(required = false) String remoteHost,
            @RequestParam(required = false) String accessToken,
            @RequestParam String defaultBranch,
            @RequestParam(defaultValue = "gradle") String buildSystem,
            @RequestParam(defaultValue = "false") boolean createIssuesDefault,
            RedirectAttributes ra
    ) {
        try {
            accessControl.requireRepositoryAccess(id);
            accessControl.requireCreateRepositoryIn(projectId);
            repositoryService.update(
                    id, projectId, name, sourceType, vcsUrl, gitlabPath, remoteHost,
                    accessToken, defaultBranch, buildSystem, createIssuesDefault);
            ra.addFlashAttribute("success", "Repository updated");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/repositories";
    }

    @PostMapping("/{id}/token")
    public String regenerateToken(@PathVariable Long id, RedirectAttributes ra) {
        try {
            accessControl.requireRepositoryManage();
            accessControl.requireRepositoryAccess(id);
            var repo = repositoryService.regenerateScanToken(id);
            ra.addFlashAttribute("success", "New scan token for #" + id + ": " + repo.getScanToken());
            ra.addFlashAttribute("createdRepoId", repo.getId());
            ra.addFlashAttribute("createdScanToken", repo.getScanToken());
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/repositories";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            accessControl.requireRepositoryManage();
            accessControl.requireRepositoryAccess(id);
            repositoryService.delete(id);
            ra.addFlashAttribute("success", "Repository deleted");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/repositories";
    }
}
