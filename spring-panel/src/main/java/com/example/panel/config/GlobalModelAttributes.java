package com.example.panel.config;

import com.example.panel.service.DatabaseHealthService;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.ui.Model;

@ControllerAdvice(annotations = Controller.class)
public class GlobalModelAttributes {

    private final Optional<DatabaseHealthService> databaseHealthService;

    public GlobalModelAttributes(ObjectProvider<DatabaseHealthService> databaseHealthService) {
        this.databaseHealthService = Optional.ofNullable(databaseHealthService.getIfAvailable());
    }

    @ModelAttribute
    public void addDatabaseWarning(Model model) {
        databaseHealthService.ifPresent(service ->
            service.detectProblem().ifPresent(message -> {
                model.addAttribute("dbWarning", message);
                model.addAttribute("dbPath", service.databasePath());
            })
        );
    }
}
