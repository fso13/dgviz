package io.github.dgviz.web;

import io.github.dgviz.security.AccessControlService;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class WebExceptionHandler {

    @ExceptionHandler(AccessControlService.AccessDeniedException.class)
    public String accessDenied(AccessControlService.AccessDeniedException ex, Model model) {
        model.addAttribute("error", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public String badRequest(IllegalArgumentException ex, Model model) {
        model.addAttribute("error", ex.getMessage());
        return "error";
    }

    @ExceptionHandler(IllegalStateException.class)
    public String illegalState(IllegalStateException ex, Model model) {
        model.addAttribute("error", ex.getMessage());
        return "error";
    }
}
