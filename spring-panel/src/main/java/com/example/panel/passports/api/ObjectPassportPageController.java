package com.example.panel.passports.api;

import com.example.panel.passports.ObjectPassportService;
import com.example.panel.service.NavigationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;
import java.util.Map;

@Controller
public class ObjectPassportPageController {

    private static final Logger log = LoggerFactory.getLogger(ObjectPassportPageController.class);

    private final NavigationService navigationService;
    private final ObjectPassportService objectPassportService;
    private final ObjectPassportPageModelAssembler pageModelAssembler;

    public ObjectPassportPageController(NavigationService navigationService,
                                        ObjectPassportService objectPassportService,
                                        ObjectPassportPageModelAssembler pageModelAssembler) {
        this.navigationService = navigationService;
        this.objectPassportService = objectPassportService;
        this.pageModelAssembler = pageModelAssembler;
    }

    @GetMapping("/object-passports")
    @PreAuthorize("hasAuthority('PAGE_OBJECT_PASSPORTS')")
    public String passports(Authentication authentication, Model model) {
        navigationService.enrich(model, authentication);
        try {
            List<Map<String, Object>> items = objectPassportService.listPassports();
            model.addAttribute("items", items);
            log.info("Loaded {} object passports for user {}", items.size(), authentication.getName());
        } catch (Exception ex) {
            log.error("Failed to load object passports page for user {}", authentication != null ? authentication.getName() : "unknown", ex);
            throw ex;
        }
        return "passports/list";
    }

    @GetMapping("/object-passports/new")
    @PreAuthorize("hasAuthority('PAGE_OBJECT_PASSPORTS')")
    public String newPassport(Authentication authentication, Model model) {
        navigationService.enrich(model, authentication);
        pageModelAssembler.populatePassportEditor(model, true);
        return "passports/new";
    }

    @GetMapping("/object-passports/{id}")
    @PreAuthorize("hasAuthority('PAGE_OBJECT_PASSPORTS')")
    public String passportDetails(@PathVariable Long id, Authentication authentication, Model model) {
        navigationService.enrich(model, authentication);
        pageModelAssembler.populatePassportEditor(model, false);
        model.addAttribute("passportEditMode", false);
        return "passports/detail";
    }

    @GetMapping("/object-passports/{id}/edit")
    @PreAuthorize("hasAuthority('PAGE_OBJECT_PASSPORTS')")
    public String passportEdit(@PathVariable Long id, Authentication authentication, Model model) {
        navigationService.enrich(model, authentication);
        pageModelAssembler.populatePassportEditor(model, false);
        model.addAttribute("passportEditMode", true);
        return "passports/detail";
    }

    @GetMapping("/object-passports/{id}/legacy-edit")
    @PreAuthorize("hasAuthority('PAGE_OBJECT_PASSPORTS')")
    public String passportLegacyEdit(@PathVariable Long id, Authentication authentication, Model model) {
        navigationService.enrich(model, authentication);
        pageModelAssembler.populatePassportEditor(model, false);
        return "passports/new";
    }

}
