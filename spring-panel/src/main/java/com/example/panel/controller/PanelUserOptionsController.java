package com.example.panel.controller;

import com.example.panel.repository.PanelUserRepository;
import java.util.Comparator;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Минимальный справочник для legacy/global назначений; scoped candidates его не используют. */
@RestController
public class PanelUserOptionsController {
    private final PanelUserRepository users;

    public PanelUserOptionsController(PanelUserRepository users) { this.users = users; }

    @GetMapping("/api/panel-users/options")
    @PreAuthorize("hasAnyAuthority('PAGE_DIALOGS','PAGE_TASKS','PAGE_OBJECT_PASSPORTS','PAGE_SETTINGS','PAGE_USERS')")
    public List<Option> options() {
        return users.findEmployeeOptions().stream()
            .filter(user -> user.enabled() && !user.blocked() && user.identityCount() == 1)
            .filter(user -> user.username() != null && !user.username().isBlank())
            .map(user -> new Option(user.username(), user.fullName() == null || user.fullName().isBlank()
                ? user.username() : user.fullName() + " · " + user.username()))
            .sorted(Comparator.comparing(Option::label, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public record Option(String value, String label) {}
}
