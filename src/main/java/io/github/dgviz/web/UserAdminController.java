package io.github.dgviz.web;

import io.github.dgviz.user.UserRole;
import io.github.dgviz.user.UserService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/users")
public class UserAdminController {

    private final UserService userService;

    public UserAdminController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping
    public String list(Model model) {
        model.addAttribute("users", userService.findAll());
        model.addAttribute("roles", UserRole.values());
        return "admin/users";
    }

    @PostMapping
    public String create(
            @RequestParam String username,
            @RequestParam String displayName,
            @RequestParam String email,
            @RequestParam UserRole role,
            @RequestParam(defaultValue = "false") boolean canManageProjects,
            @RequestParam(defaultValue = "false") boolean canManageRepositories,
            @RequestParam(defaultValue = "false") boolean canManageGroups,
            RedirectAttributes ra
    ) {
        try {
            var result = userService.createWithInvite(
                    username, displayName, email, role,
                    canManageProjects, canManageRepositories, canManageGroups);
            if (result.emailSent()) {
                ra.addFlashAttribute("success",
                        "User created. Invite emailed to " + email
                                + " (expires " + result.invite().getExpiresAt() + ")");
            } else {
                ra.addFlashAttribute("error",
                        "User created, but invite email was not sent. Copy link manually: "
                                + result.inviteLink());
            }
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}")
    public String update(
            @PathVariable Long id,
            @RequestParam String displayName,
            @RequestParam(required = false) String email,
            @RequestParam UserRole role,
            @RequestParam(defaultValue = "false") boolean enabled,
            @RequestParam(required = false) String password,
            @RequestParam(defaultValue = "false") boolean canManageProjects,
            @RequestParam(defaultValue = "false") boolean canManageRepositories,
            @RequestParam(defaultValue = "false") boolean canManageGroups,
            RedirectAttributes ra
    ) {
        try {
            userService.update(
                    id, displayName, email, role, enabled, password,
                    canManageProjects, canManageRepositories, canManageGroups);
            ra.addFlashAttribute("success", "User updated");
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/invite")
    public String resendInvite(@PathVariable Long id, RedirectAttributes ra) {
        try {
            var result = userService.resendInvite(id);
            if (result.emailSent()) {
                ra.addFlashAttribute("success",
                        "Invite resent to " + result.invite().getEmail()
                                + " (expires " + result.invite().getExpiresAt() + ")");
            } else {
                ra.addFlashAttribute("error",
                        "Invite recreated, but email was not sent. Copy link: " + result.inviteLink());
            }
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
        }
        return "redirect:/admin/users";
    }

    @PostMapping("/{id}/delete")
    public String delete(@PathVariable Long id, RedirectAttributes ra) {
        userService.delete(id);
        ra.addFlashAttribute("success", "User deleted");
        return "redirect:/admin/users";
    }
}
