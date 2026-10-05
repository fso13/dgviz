package io.github.dgviz.web;

import io.github.dgviz.group.GroupService;
import io.github.dgviz.security.AccessControlService;
import io.github.dgviz.sync.SyncConfigService;
import io.github.dgviz.user.AppUser;
import io.github.dgviz.user.UserService;
import io.github.dgviz.vulnerability.VulnerabilityRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class DashboardController {

    private final UserService userService;
    private final GroupService groupService;
    private final AccessControlService accessControl;
    private final VulnerabilityRepository vulnerabilityRepository;
    private final SyncConfigService syncConfigService;

    public DashboardController(
            UserService userService,
            GroupService groupService,
            AccessControlService accessControl,
            VulnerabilityRepository vulnerabilityRepository,
            SyncConfigService syncConfigService
    ) {
        this.userService = userService;
        this.groupService = groupService;
        this.accessControl = accessControl;
        this.vulnerabilityRepository = vulnerabilityRepository;
        this.syncConfigService = syncConfigService;
    }

    @GetMapping("/")
    public String dashboard(Model model) {
        AppUser user = accessControl.currentUser();
        boolean admin = accessControl.isAdmin(user);
        model.addAttribute("adminView", admin);
        model.addAttribute("projectCount", accessControl.accessibleProjects().size());
        model.addAttribute("repoCount", accessControl.accessibleRepositories().size());
        model.addAttribute("vulnCount", vulnerabilityRepository.count());
        if (admin) {
            model.addAttribute("userCount", userService.findAll().size());
            model.addAttribute("groupCount", groupService.findAll().size());
            model.addAttribute("sources", syncConfigService.findAll());
            model.addAttribute("runs", syncConfigService.recentRuns());
        }
        return "dashboard";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }
}
