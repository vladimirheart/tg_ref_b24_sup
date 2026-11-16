package com.example.panel.controller;

import com.example.panel.model.knowledge.KnowledgeArticleCommand;
import com.example.panel.model.knowledge.KnowledgeArticleDetails;
import com.example.panel.model.knowledge.KnowledgeBaseNotionConfigForm;
import com.example.panel.service.KnowledgeBaseNotionService;
import com.example.panel.service.KnowledgeBaseService;
import com.example.panel.service.KnowledgeAccessService;
import com.example.panel.service.KnowledgeSectionService;
import com.example.panel.service.NavigationService;
import com.example.panel.service.NotificationRoutingService;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Controller
@RequestMapping("/knowledge-base")
@Validated
public class KnowledgeBaseController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseController.class);

    private final KnowledgeBaseService knowledgeBaseService;
    private final KnowledgeAccessService knowledgeAccessService;
    private final KnowledgeSectionService knowledgeSectionService;
    private final KnowledgeBaseNotionService knowledgeBaseNotionService;
    private final AttachmentService attachmentService;
    private final NavigationService navigationService;
    private final NotificationRoutingService notificationRoutingService;

    public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService,
                                   KnowledgeAccessService knowledgeAccessService,
                                   KnowledgeSectionService knowledgeSectionService,
                                   KnowledgeBaseNotionService knowledgeBaseNotionService,
                                   AttachmentService attachmentService,
                                   NavigationService navigationService,
                                   NotificationRoutingService notificationRoutingService) {
        this.knowledgeBaseService = knowledgeBaseService;
        this.knowledgeAccessService = knowledgeAccessService;
        this.knowledgeSectionService = knowledgeSectionService;
        this.knowledgeBaseNotionService = knowledgeBaseNotionService;
        this.attachmentService = attachmentService;
        this.navigationService = navigationService;
        this.notificationRoutingService = notificationRoutingService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String list(Authentication authentication, Model model) {
        populateListModel(authentication, model);
        log.info("Knowledge base list requested by {}: {} articles, canCreate={}",
            authentication != null ? authentication.getName() : "anonymous",
            ((List<?>) model.getAttribute("articles")).size(),
            model.getAttribute("canCreate"));
        return "knowledge/list";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String createForm(Authentication authentication, Model model) {
        populateArticleModel(authentication, model, emptyArticle());
        return "knowledge/editor";
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String edit(@PathVariable Long id, Authentication authentication, Model model) {
        Optional<KnowledgeArticleDetails> article = knowledgeBaseService.findArticle(id, authentication);
        if (article.isPresent()) {
            populateArticleModel(authentication, model, article.get());
            log.info("Editing knowledge article {} requested by {}", id,
                authentication != null ? authentication.getName() : "anonymous");
        } else {
            log.warn("Knowledge article {} not found for user {}", id,
                authentication != null ? authentication.getName() : "anonymous");
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Статья не найдена");
        }
        return "knowledge/editor";
    }

    @PostMapping("/{id}/sync-notion")
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String syncArticleFromNotion(@PathVariable Long id,
                                        Authentication authentication,
                                        RedirectAttributes redirectAttributes) {
        if (!knowledgeAccessService.canEditArticle(authentication, id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Статья не найдена");
        }
        try {
            var result = knowledgeBaseNotionService.syncArticleById(id);
            String actor = authentication != null ? authentication.getName() : null;
            String message = "Статья обновлена из Notion: создано " + result.created()
                + ", обновлено " + result.updated()
                + ", пропущено " + result.skipped() + ".";
            notificationRoutingService.notify("knowledge_base", "notion_sync", Set.of(), message, "/knowledge-base/" + id, actor);
            redirectAttributes.addFlashAttribute("messageType", "success");
            redirectAttributes.addFlashAttribute("message", message);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("messageType", "danger");
            redirectAttributes.addFlashAttribute("message", ex.getMessage());
        }
        return "redirect:/knowledge-base/" + id;
    }

    @PostMapping("/articles")
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String saveArticle(KnowledgeArticleCommand command, Authentication authentication) {
        boolean isNew = command != null && command.id() == null;
        KnowledgeArticleDetails saved = knowledgeBaseService.saveArticle(command, authentication);
        String actor = authentication != null ? authentication.getName() : null;
        String title = saved.title() != null && !saved.title().isBlank() ? saved.title().trim() : "без названия";
        String text = isNew
            ? "Новая статья в базе знаний: " + title
            : "Обновлена статья в базе знаний: " + title;
        notificationRoutingService.notify("knowledge_base", "article_saved", Set.of(), text, "/knowledge-base/" + saved.id(), actor);
        return "redirect:/knowledge-base/" + saved.id();
    }

    @PostMapping("/attachments")
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String upload(Authentication authentication,
                         @RequestParam("file") MultipartFile file,
                         Model model) throws IOException {
        if (file != null && !file.isEmpty()) {
            attachmentService.storeKnowledgeBaseFile(authentication, file);
        }
        model.addAttribute("message", "Файл загружен");
        return "redirect:/knowledge-base";
    }

    @PostMapping("/articles/{id}/attachments")
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String uploadArticleAttachment(@PathVariable long id, Authentication authentication,
                                          @RequestParam("file") MultipartFile file) throws IOException {
        if (!knowledgeAccessService.canEditArticle(authentication, id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Статья не найдена");
        }
        if (file != null && !file.isEmpty()) {
            var metadata = attachmentService.storeKnowledgeBaseFile(authentication, file);
            try {
                knowledgeBaseService.attachFile(id, metadata, authentication);
            } catch (RuntimeException ex) {
                attachmentService.deleteKnowledgeBaseFile(metadata.storedName());
                throw ex;
            }
        }
        return "redirect:/knowledge-base/" + id;
    }

    @GetMapping("/sections/new")
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String createSection(Authentication authentication, Model model) {
        if (!knowledgeAccessService.canCreateSection(authentication)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Раздел не найден");
        }
        populateSectionModel(authentication, model, null);
        return "knowledge/section-editor";
    }

    @GetMapping("/sections/{id}")
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String editSection(@PathVariable long id, Authentication authentication, Model model) {
        var section = knowledgeSectionService.findVisible(id, authentication)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Раздел не найден"));
        populateSectionModel(authentication, model, section);
        return "knowledge/section-editor";
    }

    @PostMapping("/sections")
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String saveSection(@RequestParam(name = "id", required = false) Long id,
                              @RequestParam String title,
                              @RequestParam(name = "parentId", required = false) Long parentId,
                              Authentication authentication) {
        var saved = knowledgeSectionService.save(id, title, parentId, authentication);
        return "redirect:/knowledge-base/sections/" + saved.id();
    }

    @PostMapping("/sections/{id}/access")
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String addSectionAccess(@PathVariable long id,
                                   @RequestParam KnowledgeAccessService.SubjectType subjectType,
                                   @RequestParam long subjectId,
                                   @RequestParam KnowledgeAccessService.AccessLevel accessLevel,
                                   Authentication authentication) {
        knowledgeAccessService.addGrant(KnowledgeAccessService.ResourceType.SECTION, id, subjectType, subjectId, accessLevel, authentication);
        return "redirect:/knowledge-base/sections/" + id;
    }

    @PostMapping("/sections/{id}/access/{grantId}/delete")
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String deleteSectionAccess(@PathVariable long id, @PathVariable long grantId, Authentication authentication) {
        knowledgeAccessService.removeGrant(KnowledgeAccessService.ResourceType.SECTION, id, grantId, authentication);
        return "redirect:/knowledge-base/sections/" + id;
    }

    @PostMapping("/articles/{id}/access")
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String addArticleAccess(@PathVariable long id,
                                   @RequestParam KnowledgeAccessService.SubjectType subjectType,
                                   @RequestParam long subjectId,
                                   @RequestParam KnowledgeAccessService.AccessLevel accessLevel,
                                   Authentication authentication) {
        knowledgeAccessService.addGrant(KnowledgeAccessService.ResourceType.ARTICLE, id, subjectType, subjectId, accessLevel, authentication);
        return "redirect:/knowledge-base/" + id;
    }

    @PostMapping("/articles/{id}/access/{grantId}/delete")
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String deleteArticleAccess(@PathVariable long id, @PathVariable long grantId, Authentication authentication) {
        knowledgeAccessService.removeGrant(KnowledgeAccessService.ResourceType.ARTICLE, id, grantId, authentication);
        return "redirect:/knowledge-base/" + id;
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

    @PostMapping("/notion/import/preview")
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String previewImportFromNotion(Authentication authentication, Model model) {
        populateListModel(authentication, model);
        try {
            var preview = knowledgeBaseNotionService.previewImportArticles();
            model.addAttribute("notionImportPreview", preview);
            model.addAttribute("notionMessageType", "success");
            model.addAttribute(
                "notionMessage",
                "Подготовлен список статей для импорта: " + preview.matchedPages() + " из " + preview.totalPages() + "."
            );
        } catch (Exception ex) {
            model.addAttribute("notionMessageType", "danger");
            model.addAttribute("notionMessage", ex.getMessage());
        }
        return "knowledge/list";
    }

    @PostMapping("/notion/import")
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String importFromNotion(Authentication authentication,
                                  @RequestParam(name = "selectedExternalIds", required = false) List<String> selectedExternalIds,
                                  RedirectAttributes redirectAttributes) {
        try {
            var result = selectedExternalIds == null
                ? knowledgeBaseNotionService.importArticles()
                : knowledgeBaseNotionService.importArticles(selectedExternalIds);
            String actor = authentication != null ? authentication.getName() : null;
            String message = "Импорт Notion завершён: выбрано " + result.selectedPages()
                + " из " + result.matchedPages()
                + ", создано " + result.created()
                + ", обновлено " + result.updated()
                + ", пропущено " + result.skipped()
                + ", всего в источнике " + result.totalPages() + ".";
            notificationRoutingService.notify("knowledge_base", "notion_sync", Set.of(), message, "/knowledge-base", actor);
            redirectAttributes.addFlashAttribute("notionMessageType", "success");
            redirectAttributes.addFlashAttribute("notionMessage", message);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("notionMessageType", "danger");
            redirectAttributes.addFlashAttribute("notionMessage", ex.getMessage());
        }
        return "redirect:/knowledge-base#notion-import";
    }

    @PostMapping("/notion/sync-changed")
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String syncChangedFromNotion(Authentication authentication, RedirectAttributes redirectAttributes) {
        try {
            var result = knowledgeBaseNotionService.importChangedArticles();
            String actor = authentication != null ? authentication.getName() : null;
            String message = "Синхронизация изменённых статей Notion завершена: найдено изменений " + result.selectedPages()
                + ", создано " + result.created()
                + ", обновлено " + result.updated()
                + ", пропущено " + result.skipped()
                + ", всего в источнике " + result.totalPages() + ".";
            notificationRoutingService.notify("knowledge_base", "notion_import", Set.of(), message, "/knowledge-base", actor);
            redirectAttributes.addFlashAttribute("notionMessageType", "success");
            redirectAttributes.addFlashAttribute("notionMessage", message);
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("notionMessageType", "danger");
            redirectAttributes.addFlashAttribute("notionMessage", ex.getMessage());
        }
        return "redirect:/knowledge-base#notion-import";
    }

    private void populateListModel(Authentication authentication, Model model) {
        navigationService.enrich(model, authentication);
        var articles = knowledgeBaseService.listArticles(authentication);
        var canCreate = knowledgeAccessService.canCreateArticle(authentication);
        model.addAttribute("articles", articles);
        model.addAttribute("sections", knowledgeSectionService.listVisible(authentication));
        model.addAttribute("canCreate", canCreate);
        model.addAttribute("canManageKnowledge", knowledgeAccessService.canCreateSection(authentication));
        if (!model.containsAttribute("notionConfig")) {
            model.addAttribute("notionConfig", knowledgeBaseNotionService.buildForm());
        }
        model.addAttribute("notionHasToken", knowledgeBaseNotionService.hasSavedToken());
        model.addAttribute("notionSyncStatus", knowledgeBaseNotionService.getSyncStatus());
    }

    private void populateArticleModel(Authentication authentication, Model model, KnowledgeArticleDetails article) {
        navigationService.enrich(model, authentication);
        model.addAttribute("article", article);
        model.addAttribute("sections", knowledgeSectionService.listVisible(authentication));
        boolean canEdit = article.id() == null
            ? knowledgeAccessService.canCreateArticle(authentication)
            : knowledgeAccessService.canEditArticle(authentication, article.id());
        boolean canManage = article.id() != null && knowledgeAccessService.canManageArticle(authentication, article.id());
        boolean canMove = article.id() == null || (article.sectionId() != null
            && knowledgeAccessService.canEditSection(authentication, article.sectionId()));
        model.addAttribute("canEditArticle", canEdit);
        model.addAttribute("canManageArticle", canManage);
        model.addAttribute("canMoveArticle", canMove);
        model.addAttribute("articleAccessGrants", canManage
            ? knowledgeAccessService.listGrants(KnowledgeAccessService.ResourceType.ARTICLE, article.id(), authentication) : List.of());
        model.addAttribute("accessSubjects", canManage ? knowledgeAccessService.listSubjects(authentication) : List.of());
    }

    private void populateSectionModel(Authentication authentication, Model model, KnowledgeSectionService.Section section) {
        navigationService.enrich(model, authentication);
        model.addAttribute("section", section);
        model.addAttribute("sections", knowledgeSectionService.listVisible(authentication));
        boolean canManage = section != null && knowledgeAccessService.canManageSection(authentication, section.id());
        model.addAttribute("canManageSection", canManage);
        model.addAttribute("sectionAccessGrants", canManage
            ? knowledgeAccessService.listGrants(KnowledgeAccessService.ResourceType.SECTION, section.id(), authentication) : List.of());
        model.addAttribute("accessSubjects", canManage ? knowledgeAccessService.listSubjects(authentication) : List.of());
    }

    private KnowledgeArticleDetails emptyArticle() {
        return new KnowledgeArticleDetails(null, "", null, null, "", "", "draft", "", "", "", "", "", "",
            null, null, null, null, null, null, List.of(), List.of(), List.of());
    }
}
