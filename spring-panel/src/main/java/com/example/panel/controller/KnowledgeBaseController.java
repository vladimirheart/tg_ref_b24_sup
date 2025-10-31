package com.example.panel.controller;

import com.example.panel.service.PermissionService;
import com.example.panel.storage.AttachmentService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/knowledge-base")
@Validated
public class KnowledgeBaseController {

    private final PermissionService permissionService;
    private final AttachmentService attachmentService;

    public KnowledgeBaseController(PermissionService permissionService, AttachmentService attachmentService) {
        this.permissionService = permissionService;
        this.attachmentService = attachmentService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String list(Authentication authentication, Model model) {
        model.addAttribute("articles", List.of());
        model.addAttribute("canCreate", permissionService.hasAuthority(authentication, "PAGE_KNOWLEDGE_BASE"));
        return "knowledge/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String createForm(Model model) {
        model.addAttribute("article", Map.of("id", null, "title", "", "department", "", "summary", "", "content", ""));
        return "knowledge/editor";
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String edit(@PathVariable Long id, Model model) {
        model.addAttribute("article", Map.of("id", id, "title", "Черновик", "department", "", "summary", "", "content", ""));
        return "knowledge/editor";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String upload(Authentication authentication, @RequestParam("file") MultipartFile file, Model model) throws IOException {
        if (file != null && !file.isEmpty()) {
            attachmentService.storeKnowledgeBaseFile(authentication, file);
        }
        model.addAttribute("message", "Файл загружен");
        return "redirect:/knowledge-base";
    }
}
