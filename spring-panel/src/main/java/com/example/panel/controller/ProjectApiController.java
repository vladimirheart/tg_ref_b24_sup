package com.example.panel.controller;

import com.example.panel.service.ProjectService;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/projects")
@PreAuthorize("hasAuthority('PAGE_TASKS')")
public class ProjectApiController {

    private final ProjectService projectService;

    public ProjectApiController(ProjectService projectService) {
        this.projectService = projectService;
    }

    @GetMapping
    public Map<String, Object> list(
            @RequestParam(name = "include_archived", defaultValue = "false") boolean includeArchived) {
        return projectService.list(includeArchived);
    }

    @GetMapping("/{id}")
    public Map<String, Object> get(@PathVariable Long id) {
        return projectService.get(id);
    }

    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> payload,
                                      Authentication authentication) {
        return Map.of(
            "ok", true,
            "project", projectService.create(payload, actor(authentication))
        );
    }

    @PatchMapping("/{id}")
    public Map<String, Object> update(@PathVariable Long id,
                                      @RequestBody Map<String, Object> payload,
                                      Authentication authentication) {
        return Map.of(
            "ok", true,
            "project", projectService.update(id, payload, actor(authentication))
        );
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> archive(@PathVariable Long id,
                                       Authentication authentication) {
        return Map.of(
            "ok", true,
            "project", projectService.archive(id, actor(authentication))
        );
    }

    private String actor(Authentication authentication) {
        return authentication != null ? authentication.getName() : null;
    }
}
