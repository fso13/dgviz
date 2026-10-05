package io.github.dgviz.web;

import io.github.dgviz.user.UserInvite;
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
@RequestMapping("/invite")
public class InviteController {

    private final UserService userService;

    public InviteController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/{token}")
    public String show(@PathVariable String token, Model model) {
        try {
            UserInvite invite = userService.requireActiveInvite(token);
            model.addAttribute("token", token);
            model.addAttribute("username", invite.getUser().getUsername());
            model.addAttribute("displayName", invite.getUser().getDisplayName());
            model.addAttribute("email", invite.getEmail());
            model.addAttribute("expiresAt", invite.getExpiresAt());
            return "invite/set-password";
        } catch (Exception ex) {
            model.addAttribute("error", ex.getMessage());
            return "invite/invalid";
        }
    }

    @PostMapping("/{token}")
    public String accept(
            @PathVariable String token,
            @RequestParam String password,
            @RequestParam String passwordConfirm,
            RedirectAttributes ra
    ) {
        try {
            if (!password.equals(passwordConfirm)) {
                throw new IllegalArgumentException("Passwords do not match");
            }
            userService.acceptInvite(token, password);
            ra.addFlashAttribute("inviteSuccess", "Password set. You can sign in now.");
            return "redirect:/login";
        } catch (Exception ex) {
            ra.addFlashAttribute("error", ex.getMessage());
            return "redirect:/invite/" + token;
        }
    }
}
