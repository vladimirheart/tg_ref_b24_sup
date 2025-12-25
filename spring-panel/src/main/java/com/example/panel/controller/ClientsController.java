package com.example.panel.controller;

import com.example.panel.entity.BotUser;
import com.example.panel.repository.BotUserRepository;
import com.example.panel.service.NavigationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
public class ClientsController {

    private static final Logger log = LoggerFactory.getLogger(ClientsController.class);

    private final NavigationService navigationService;
    private final BotUserRepository botUserRepository;

    public ClientsController(NavigationService navigationService,
                             BotUserRepository botUserRepository) {
        this.navigationService = navigationService;
        this.botUserRepository = botUserRepository;
    }

    @GetMapping("/clients")
    @PreAuthorize("hasAuthority('PAGE_CLIENTS')")
    public String clients(Authentication authentication, Model model) {
        navigationService.enrich(model, authentication);
        try {
            List<BotUser> clients = botUserRepository.findAll();
            model.addAttribute("clients", clients);
            log.info("Loaded {} clients for user {}", clients.size(), authentication != null ? authentication.getName() : "unknown");
        } catch (Exception ex) {
            log.error("Failed to load clients page for user {}", authentication != null ? authentication.getName() : "unknown", ex);
            throw ex;
        }
        return "clients/index";
    }
}
