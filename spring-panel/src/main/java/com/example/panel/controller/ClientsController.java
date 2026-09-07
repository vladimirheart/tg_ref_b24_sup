package com.example.panel.controller;

import com.example.panel.model.clients.ClientListItem;
import com.example.panel.model.clients.ClientProfile;
import com.example.panel.service.ClientsService;
import com.example.panel.service.NavigationService;
import com.example.panel.service.SharedConfigService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Controller
public class ClientsController {

    private static final Logger log = LoggerFactory.getLogger(ClientsController.class);

    private final NavigationService navigationService;
    private final ClientsService clientsService;
    private final SharedConfigService sharedConfigService;

    public ClientsController(NavigationService navigationService,
                             ClientsService clientsService,
                             SharedConfigService sharedConfigService) {
        this.navigationService = navigationService;
        this.clientsService = clientsService;
        this.sharedConfigService = sharedConfigService;
    }

    @GetMapping("/clients")
    @PreAuthorize("hasAuthority('PAGE_CLIENTS')")
    public String clients(Authentication authentication,
                          @RequestParam(name = "blacklist", required = false) String blacklistFilter,
                          @RequestParam(name = "client_status", required = false) String statusFilter,
                          Model model) {
        navigationService.enrich(model, authentication);
        try {
            List<ClientListItem> clients = clientsService.loadClients(blacklistFilter, statusFilter);
            model.addAttribute("clients", clients);
            model.addAttribute("blacklistFilter", blacklistFilter == null ? "" : blacklistFilter);
            model.addAttribute("statusFilter", statusFilter == null ? "" : statusFilter);
            Map<String, Object> settings = sharedConfigService.loadSettings();
            Object statusColors = settings.getOrDefault("client_status_colors", Map.of());
            model.addAttribute("statusColors", statusColors);
            log.info("Loaded {} clients for user {}", clients.size(), authentication != null ? authentication.getName() : "unknown");
        } catch (Exception ex) {
            log.error("Failed to load clients page for user {}", authentication != null ? authentication.getName() : "unknown", ex);
            throw ex;
        }
        return "clients/index";
    }

    @GetMapping("/client/{userId}")
    @PreAuthorize("hasAuthority('PAGE_CLIENTS')")
    public String clientProfile(@PathVariable("userId") long userId,
                                Authentication authentication,
                                Model model) {
        navigationService.enrich(model, authentication);
        Optional<ClientProfile> profile = clientsService.loadClientProfile(userId);
        if (profile.isEmpty()) {
            return "redirect:/clients";
        }
        model.addAttribute("profile", profile.get());
        Map<String, Object> settings = sharedConfigService.loadSettings();
        model.addAttribute("clientStatuses", resolveClientStatusOptions(settings, profile.get()));
        model.addAttribute("clientStatusColors", settings.getOrDefault("client_status_colors", Map.of()));
        return "clients/profile";
    }

    private List<String> resolveClientStatusOptions(Map<String, Object> settings, ClientProfile profile) {
        LinkedHashSet<String> statuses = new LinkedHashSet<>();
        addStatusValues(statuses, settings.get("client_statuses"));

        Object rawColors = settings.get("client_status_colors");
        if (rawColors instanceof Map<?, ?> colors) {
            for (Object rawStatus : colors.keySet()) {
                addStatusValue(statuses, rawStatus);
            }
        }

        for (String status : clientsService.loadKnownClientStatuses()) {
            addStatusValue(statuses, status);
        }

        if (profile != null) {
            addStatusValue(statuses, profile.clientStatus());
        }

        return List.copyOf(statuses);
    }

    private void addStatusValues(Set<String> statuses, Object raw) {
        if (!(raw instanceof Iterable<?> values)) {
            return;
        }
        for (Object value : values) {
            addStatusValue(statuses, value);
        }
    }

    private void addStatusValue(Set<String> statuses, Object raw) {
        if (raw == null) {
            return;
        }
        String value = String.valueOf(raw).trim();
        if (!value.isEmpty()) {
            statuses.add(value);
        }
    }
}
