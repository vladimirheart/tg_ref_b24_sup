package com.example.panel.controller;

import com.example.panel.model.knowledge.KnowledgeArticleCommand;
import com.example.panel.model.knowledge.KnowledgeArticleDetails;
import com.example.panel.service.KnowledgeBaseService;
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
import java.util.Optional;

@Controller
@RequestMapping("/knowledge-base")
@Validated
public class KnowledgeBaseController {

    private final PermissionService permissionService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final AttachmentService attachmentService;

    public KnowledgeBaseController(PermissionService permissionService,
                                   KnowledgeBaseService knowledgeBaseService,
                                   AttachmentService attachmentService) {
        this.permissionService = permissionService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.attachmentService = attachmentService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String list(Authentication authentication, Model model) {
        model.addAttribute("articles", knowledgeBaseService.listArticles());
        model.addAttribute("canCreate", permissionService.hasAuthority(authentication, "PAGE_KNOWLEDGE_BASE"));
        return "knowledge/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String createForm(Model model) {
        model.addAttribute("article", emptyArticle());
        return "knowledge/editor";
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String edit(@PathVariable Long id, Model model) {
        Optional<KnowledgeArticleDetails> article = knowledgeBaseService.findArticle(id);
        model.addAttribute("article", article.orElseGet(this::emptyArticle));
        return "knowledge/editor";
    }

    @PostMapping("/articles")
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String saveArticle(KnowledgeArticleCommand command) {
        KnowledgeArticleDetails saved = knowledgeBaseService.saveArticle(command);
        return "redirect:/knowledge-base/" + saved.id();
    }

    @PostMapping("/attachments")
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String upload(Authentication authentication, @RequestParam("file") MultipartFile file, Model model) throws IOException {
        if (file != null && !file.isEmpty()) {
            attachmentService.storeKnowledgeBaseFile(authentication, file);
        }
        model.addAttribute("message", "Файл загружен");
        return "redirect:/knowledge-base";
    }

    private KnowledgeArticleDetails emptyArticle() {
        return new KnowledgeArticleDetails(null, "", "", "", "draft", "", "", "", "", "", null, null, List.of());
    }
}