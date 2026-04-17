package com.example.panel.controller;

import com.example.panel.model.knowledge.KnowledgeArticleCommand;
import com.example.panel.model.knowledge.KnowledgeArticleDetails;
import com.example.panel.model.knowledge.KnowledgeBaseNotionConfigForm;
import com.example.panel.service.KnowledgeBaseService;
import com.example.panel.service.KnowledgeBaseNotionService;
import com.example.panel.service.NavigationService;
import com.example.panel.service.NotificationService;
import com.example.panel.service.PermissionService;
import com.example.panel.storage.AttachmentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@Controller
@RequestMapping("/knowledge-base")
@Validated
public class KnowledgeBaseController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseController.class);

    private final PermissionService permissionService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeBaseNotionService knowledgeBaseNotionService;
    private final AttachmentService attachmentService;
    private final NavigationService navigationService;
    private final NotificationService notificationService;

    public KnowledgeBaseController(PermissionService permissionService,
                                   KnowledgeBaseService knowledgeBaseService,
                                   KnowledgeBaseNotionService knowledgeBaseNotionService,
                                   AttachmentService attachmentService,
                                   NavigationService navigationService,
                                   NotificationService notificationService) {
        this.permissionService = permissionService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.knowledgeBaseNotionService = knowledgeBaseNotionService;
        this.attachmentService = attachmentService;
        this.navigationService = navigationService;
        this.notificationService = notificationService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String list(Authentication authentication, Model model) {
        navigationService.enrich(model, authentication);
        var articles = knowledgeBaseService.listArticles();
        var canCreate = permissionService.hasAuthority(authentication, "PAGE_KNOWLEDGE_BASE");
        model.addAttribute("articles", articles);
        model.addAttribute("canCreate", canCreate);
        if (!model.containsAttribute("notionConfig")) {
            model.addAttribute("notionConfig", knowledgeBaseNotionService.buildForm());
        }
        model.addAttribute("notionHasToken", knowledgeBaseNotionService.hasSavedToken());
        log.info("Knowledge base list requested by {}: {} articles, canCreate={}"
                , authentication != null ? authentication.getName() : "anonymous", articles.size(), canCreate);
        return "knowledge/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String createForm(Authentication authentication, Model model) {
        navigationService.enrich(model, authentication);
        model.addAttribute("article", emptyArticle());
        return "knowledge/editor";
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String edit(@PathVariable Long id, Authentication authentication, Model model) {
        navigationService.enrich(model, authentication);
        Optional<KnowledgeArticleDetails> article = knowledgeBaseService.findArticle(id);
        model.addAttribute("article", article.orElseGet(this::emptyArticle));
        if (article.isPresent()) {
            log.info("Editing knowledge article {} requested by {}", id,
                    authentication != null ? authentication.getName() : "anonymous");
        } else {
            log.warn("Knowledge article {} not found for user {}", id,
                    authentication != null ? authentication.getName() : "anonymous");
        }
        return "knowledge/editor";
    }

    @PostMapping("/articles")
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String saveArticle(KnowledgeArticleCommand command, Authentication authentication) {
        boolean isNew = command != null && command.id() == null;
        KnowledgeArticleDetails saved = knowledgeBaseService.saveArticle(command);
        String actor = authentication != null ? authentication.getName() : null;
        String title = saved.title() != null && !saved.title().isBlank() ? saved.title().trim() : "без названия";
        String text = isNew
                ? "Новая статья в базе знаний: " + title
                : "Обновлена статья в базе знаний: " + title;
        notificationService.notifyAllOperators(text, "/knowledge-base/" + saved.id(), actor);
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

    @PostMapping("/notion/settings")
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String saveNotionSettings(KnowledgeBaseNotionConfigForm form, RedirectAttributes redirectAttributes) {
        try {
            knowledgeBaseNotionService.saveConfig(form);
            redirectAttributes.addFlashAttribute("notionMessageType", "success");
            redirectAttributes.addFlashAttribute("notionMessage", "Подключение к Notion сохранено.");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("notionMessageType", "danger");
            redirectAttributes.addFlashAttribute("notionMessage", ex.getMessage());
            redirectAttributes.addFlashAttribute("notionConfig", form);
        }
        return "redirect:/knowledge-base#notion-import";
    }

    @PostMapping("/notion/test")
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String testNotionConnection(RedirectAttributes redirectAttributes) {
        try {
            var result = knowledgeBaseNotionService.testConnection();
            redirectAttributes.addFlashAttribute("notionMessageType", "success");
            redirectAttributes.addFlashAttribute(
                "notionMessage",
                "Подключение проверено: найдено " + result.matchedPages() + " статей из " + result.totalPages()
                    + " по фильтру авторов (" + result.mode() + ")."
            );
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("notionMessageType", "danger");
            redirectAttributes.addFlashAttribute("notionMessage", ex.getMessage());
        }
        return "redirect:/knowledge-base#notion-import";
    }

    @PostMapping("/notion/import")
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String importFromNotion(Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            var result = knowledgeBaseNotionService.importArticles();
            String actor = authentication != null ? authentication.getName() : null;
            String message = "Импорт Notion завершён: создано " + result.created()
                + ", обновлено " + result.updated()
                + ", пропущено " + result.skipped()
                + ", совпадений " + result.matchedPages()
                + " из " + result.totalPages() + ".";
            notificationService.notifyAllOperators(message, "/knowledge-base", actor);
            redirectAttributes.addFlashAttribute("notionMessageType", "success");
            redirectAttributes.addFlashAttribute("notionMessage", message);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("notionMessageType", "danger");
            redirectAttributes.addFlashAttribute("notionMessage", ex.getMessage());
        }
        return "redirect:/knowledge-base#notion-import";
    }

    private KnowledgeArticleDetails emptyArticle() {
        return new KnowledgeArticleDetails(null, "", "", "", "draft", "", "", "", "", "", "", null, null, List.of());
    }
}
