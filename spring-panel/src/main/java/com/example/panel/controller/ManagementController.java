package com.example.panel.controller;

import com.example.panel.entity.Channel;
import com.example.panel.entity.PanelUser;
import com.example.panel.entity.Task;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.repository.PanelUserRepository;
import com.example.panel.repository.TaskRepository;
import com.example.panel.service.NavigationService;
import com.example.panel.service.PermissionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Controller
public class ManagementController {

    private static final Logger log = LoggerFactory.getLogger(ManagementController.class);
    private final NavigationService navigationService;
    private final TaskRepository taskRepository;
    private final ChannelRepository channelRepository;
    private final PanelUserRepository panelUserRepository;
    private final PermissionService permissionService;

    public ManagementController(NavigationService navigationService,
                                TaskRepository taskRepository,
                                ChannelRepository channelRepository,
                                PanelUserRepository panelUserRepository,
                                PermissionService permissionService) {
        this.navigationService = navigationService;
        this.taskRepository = taskRepository;
        this.channelRepository = channelRepository;
        this.panelUserRepository = panelUserRepository;
        this.permissionService = permissionService;
    }

    @GetMapping("/tasks")
    @PreAuthorize("hasAuthority('PAGE_TASKS')")
    public String tasks(Authentication authentication, Model model) {
        navigationService.enrich(model, authentication);
        try {
            List<Task> tasks = taskRepository.findTop50ByOrderByCreatedAtDesc();
            model.addAttribute("tasks", tasks);
            model.addAttribute("users", panelUserRepository.findAll());
            log.info("Loaded {} tasks for user {}", tasks.size(), authentication.getName());
        } catch (Exception ex) {
            log.error("Failed to load tasks page for user {}", authentication != null ? authentication.getName() : "unknown", ex);
            throw ex;
        }
        return "tasks/index";
    }

    @GetMapping("/channels")
    @PreAuthorize("hasAuthority('PAGE_CHANNELS')")
    public String channels(Authentication authentication, Model model) {
        if (permissionService.hasAuthority(authentication, "PAGE_SETTINGS")) {
            return "redirect:/settings?open=channels";
        }
        navigationService.enrich(model, authentication);
        try {
            List<Channel> channels = channelRepository.findAll();
            model.addAttribute("channels", channels);
            log.info("Loaded {} channels for user {}", channels.size(), authentication.getName());
        } catch (Exception ex) {
            log.error("Failed to load channels page for user {}", authentication != null ? authentication.getName() : "unknown", ex);
            throw ex;
        }
        return "channels/index";
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('PAGE_USERS')")
    public String users(Authentication authentication, Model model) {
        if (permissionService.hasAuthority(authentication, "PAGE_SETTINGS")) {
            return "redirect:/settings?open=users";
        }
        navigationService.enrich(model, authentication);
        try {
            List<PanelUser> users = panelUserRepository.findAll();
            model.addAttribute("users", users);
            log.info("Loaded {} panel users for user {}", users.size(), authentication.getName());
        } catch (Exception ex) {
            log.error("Failed to load users page for user {}", authentication != null ? authentication.getName() : "unknown", ex);
            throw ex;
        }
        return "users/index";
    }


    @GetMapping("/users/{username}")
    @PreAuthorize("hasAuthority('PAGE_USERS')")
    public String userCard(@PathVariable String username, Authentication authentication, Model model) {
        navigationService.enrich(model, authentication);
        PanelUser user = panelUserRepository.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new IllegalArgumentException("Пользователь не найден"));
        model.addAttribute("panelUser", user);
        return "users/detail";
    }

}
