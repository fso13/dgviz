package io.github.dgviz.web;

import io.github.dgviz.group.GroupService;
import io.github.dgviz.security.AccessControlService;
import io.github.dgviz.user.AppUser;
import io.github.dgviz.user.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Controller
@RequestMapping("/admin/groups")
public class GroupAdminController {

    private final GroupService groupService;
    private final UserService userService;
    private final AccessControlService accessControl;

    public GroupAdminController(
            GroupService groupService,
            UserService userService,
            AccessControlService accessControl
    ) {
        this.groupService = groupService;
        this.userService = userService;
        this.accessControl = accessControl;
    }

    @GetMapping
    public String list(Model model) {
        accessControl.requireGroupManage();
        AppUser user = accessControl.currentUser();
        model.addAttribute("groups", accessControl.manageableGroups());
        model.addAttribute("users", userService.findAll());
        model.addAttribute("adminView", accessControl.isAdmin(user));
        model.addAttribute("canCreate", accessControl.isAdmin(user));
        return "admin/groups";
    }

    @PostMapping
    public String create(
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) List<Long> memberIds,
            RedirectAttributes ra
    ) {
        try {
            accessControl.requireGroupManage();
            AppUser user = accessControl.currentUser();
            if (!accessControl.isAdmin(user)) {
                throw new AccessControlService.AccessDeniedException(
                        "Only ADMIN can create new groups; managers can edit groups they belong to");
            }
            groupService.create(name, description, memberIds);
            ra.addFlashAttribute("success", "Group created");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/groups";
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable Long id,
            @RequestParam String name,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) List<Long> memberIds,
            RedirectAttributes ra
    ) {
        try {
            accessControl.requireManageGroup(id);
            AppUser user = accessControl.currentUser();
            List<Long> effectiveMembers = memberIds;
            if (!accessControl.isAdmin(user)) {
                // Group manager cannot remove themselves from the group.
                Set<Long> members = memberIds == null ? new HashSet<>() : new HashSet<>(memberIds);
                members.add(user.getId());
                effectiveMembers = List.copyOf(members);
            }
            groupService.update(id, name, description, effectiveMembers);
            ra.addFlashAttribute("success", "Group updated");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/groups";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        try {
            accessControl.requireManageGroup(id);
            AppUser user = accessControl.currentUser();
            if (!accessControl.isAdmin(user)) {
                throw new AccessControlService.AccessDeniedException("Only ADMIN can delete groups");
            }
            groupService.delete(id);
            ra.addFlashAttribute("success", "Group deleted");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/groups";
    }
}
