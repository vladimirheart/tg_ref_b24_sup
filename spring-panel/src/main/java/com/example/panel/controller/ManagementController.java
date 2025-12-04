package com.example.panel.controller;

import com.example.panel.entity.AppSetting;
import com.example.panel.entity.Channel;
import com.example.panel.entity.ItEquipmentCatalog;
import com.example.panel.entity.PanelUser;
import com.example.panel.entity.SettingsParameter;
import com.example.panel.entity.Task;
import com.example.panel.repository.AppSettingRepository;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.repository.ItEquipmentCatalogRepository;
import com.example.panel.repository.PanelUserRepository;
import com.example.panel.repository.SettingsParameterRepository;
import com.example.panel.repository.TaskRepository;
import com.example.panel.service.NavigationService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Controller
public class ManagementController {

    private final NavigationService navigationService;
    private final TaskRepository taskRepository;
    private final ChannelRepository channelRepository;
    private final PanelUserRepository panelUserRepository;
    private final AppSettingRepository appSettingRepository;
    private final SettingsParameterRepository settingsParameterRepository;
    private final ItEquipmentCatalogRepository equipmentRepository;

    public ManagementController(NavigationService navigationService,
                                TaskRepository taskRepository,
                                ChannelRepository channelRepository,
                                PanelUserRepository panelUserRepository,
                                AppSettingRepository appSettingRepository,
                                SettingsParameterRepository settingsParameterRepository,
                                ItEquipmentCatalogRepository equipmentRepository) {
        this.navigationService = navigationService;
        this.taskRepository = taskRepository;
        this.channelRepository = channelRepository;
        this.panelUserRepository = panelUserRepository;
        this.appSettingRepository = appSettingRepository;
        this.settingsParameterRepository = settingsParameterRepository;
        this.equipmentRepository = equipmentRepository;
    }

    @GetMapping("/tasks")
    @PreAuthorize("hasAuthority('PAGE_TASKS')")
    public String tasks(Authentication authentication, Model model) {
        navigationService.enrich(model, authentication);
        List<Task> tasks = taskRepository.findTop50ByOrderByCreatedAtDesc();
        model.addAttribute("tasks", tasks);
        return "tasks/index";
    }

    @GetMapping("/channels")
    @PreAuthorize("hasAuthority('PAGE_CHANNELS')")
    public String channels(Authentication authentication, Model model) {
        navigationService.enrich(model, authentication);
        List<Channel> channels = channelRepository.findAll();
        model.addAttribute("channels", channels);
        return "channels/index";
    }

    @GetMapping("/users")
    @PreAuthorize("hasAuthority('PAGE_USERS')")
    public String users(Authentication authentication, Model model) {
        navigationService.enrich(model, authentication);
        List<PanelUser> users = panelUserRepository.findAll();
        model.addAttribute("users", users);
        return "users/index";
    }

    @GetMapping("/settings")
    @PreAuthorize("hasAuthority('PAGE_SETTINGS')")
    public String settings(Authentication authentication, Model model) {
        navigationService.enrich(model, authentication);
        List<AppSetting> appSettings = appSettingRepository.findAll();
        List<SettingsParameter> systemParameters = settingsParameterRepository.findAll();
        model.addAttribute("appSettings", appSettings);
        model.addAttribute("systemParameters", systemParameters);
        return "settings/index";
    }

    @GetMapping("/object-passports")
    @PreAuthorize("hasAuthority('PAGE_OBJECT_PASSPORTS')")
    public String passports(Authentication authentication, Model model) {
        navigationService.enrich(model, authentication);
        List<ItEquipmentCatalog> items = equipmentRepository.findAll();
        model.addAttribute("items", items);
        return "passports/list";
    }

    @GetMapping("/object-passports/{id}")
    @PreAuthorize("hasAuthority('PAGE_OBJECT_PASSPORTS')")
    public String passportDetails(@PathVariable Long id, Authentication authentication, Model model) {
        navigationService.enrich(model, authentication);
        return equipmentRepository.findById(id)
                .map(item -> {
                    model.addAttribute("item", item);
                    return "passports/detail";
                })
                .orElse("redirect:/object-passports");
    }
}
