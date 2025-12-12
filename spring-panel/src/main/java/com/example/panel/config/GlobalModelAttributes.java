package com.example.panel.config;

import com.example.panel.service.DatabaseHealthService;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;

@ControllerAdvice(annotations = Controller.class)
public class GlobalModelAttributes {

    private final DatabaseHealthService databaseHealthService;

    public GlobalModelAttributes(DatabaseHealthService databaseHealthService) {
        this.databaseHealthService = databaseHealthService;
    }

    @ModelAttribute
    public void addDatabaseWarning(Model model) {
        databaseHealthService.detectProblem().ifPresent(message -> {
            model.addAttribute("dbWarning", message);
            model.addAttribute("dbPath", databaseHealthService.databasePath());
        });
    }
}
