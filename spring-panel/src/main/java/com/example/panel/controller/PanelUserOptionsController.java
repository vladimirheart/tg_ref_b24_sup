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
        return users.findAll().stream().filter(user -> !Boolean.TRUE.equals(user.getBlocked()))
            .filter(user -> user.getUsername() != null && !user.getUsername().isBlank())
            .map(user -> new Option(user.getUsername(), user.getFullName() == null || user.getFullName().isBlank()
                ? user.getUsername() : user.getFullName() + " · " + user.getUsername()))
            .sorted(Comparator.comparing(Option::label, String.CASE_INSENSITIVE_ORDER)).toList();
    }

    public record Option(String value, String label) {}
}
