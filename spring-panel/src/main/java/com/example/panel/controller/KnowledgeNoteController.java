package com.example.panel.controller;

import com.example.panel.passports.ObjectPassportService;
import com.example.panel.service.KnowledgeBaseService;
import com.example.panel.service.KnowledgeNoteService;
import com.example.panel.service.NavigationService;
import com.example.panel.service.NotificationRoutingService;
import com.example.panel.service.PermissionService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.Set;

@Controller
@RequestMapping("/knowledge-base/notes")
public class KnowledgeNoteController {

    private final KnowledgeNoteService knowledgeNoteService;
    private final KnowledgeBaseService knowledgeBaseService;
    private final ObjectPassportService objectPassportService;
    private final NavigationService navigationService;
    private final PermissionService permissionService;
    private final NotificationRoutingService notificationRoutingService;

    public KnowledgeNoteController(KnowledgeNoteService knowledgeNoteService,
                                   KnowledgeBaseService knowledgeBaseService,
                                   ObjectPassportService objectPassportService,
                                   NavigationService navigationService,
                                   PermissionService permissionService,
                                   NotificationRoutingService notificationRoutingService) {
        this.knowledgeNoteService = knowledgeNoteService;
        this.knowledgeBaseService = knowledgeBaseService;
        this.objectPassportService = objectPassportService;
        this.navigationService = navigationService;
        this.permissionService = permissionService;
        this.notificationRoutingService = notificationRoutingService;
    }

    @GetMapping
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String list(Authentication authentication, Model model) {
        navigationService.enrich(model, authentication);
        model.addAttribute("notes", knowledgeNoteService.listNotes());
        return "knowledge/notes";
    }

    @GetMapping("/new")
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String create(Authentication authentication, Model model) {
        populateEditor(authentication, model, emptyNote());
        return "knowledge/note-editor";
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String edit(@PathVariable Long id, Authentication authentication, Model model) {
        KnowledgeNoteService.NoteDetails note = knowledgeNoteService.findNote(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Заметка не найдена"));
        populateEditor(authentication, model, note);
        return "knowledge/note-editor";
    }

    @PostMapping
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String save(@RequestParam(name = "id", required = false) Long id,
                       @RequestParam String title,
                       @RequestParam(name = "body", required = false) String body,
                       @RequestParam(name = "customFieldKey", required = false) List<String> customFieldKeys,
                       @RequestParam(name = "customFieldValue", required = false) List<String> customFieldValues,
                       @RequestParam(name = "knowledgeArticleIds", required = false) List<Long> knowledgeArticleIds,
                       @RequestParam(name = "objectPassportIds", required = false) List<Long> objectPassportIds,
                       Authentication authentication) {
        String actor = authentication != null ? authentication.getName() : null;
        boolean canLinkPassports = permissionService.hasAuthority(authentication, "PAGE_OBJECT_PASSPORTS");
        List<Long> passportLinkIds = canLinkPassports
            ? (objectPassportIds == null ? List.of() : objectPassportIds)
            : null;
        long noteId = knowledgeNoteService.save(
            id, title, body, customFieldKeys, customFieldValues,
            knowledgeArticleIds,
            passportLinkIds,
            actor
        );
        String label = title != null && !title.isBlank() ? title.trim() : "без названия";
        notificationRoutingService.notify(
            "knowledge_base", "note_saved", Set.of(),
            (id == null ? "Создана заметка: " : "Обновлена заметка: ") + label,
            "/knowledge-base/notes/" + noteId,
            actor
        );
        return "redirect:/knowledge-base/notes/" + noteId;
    }

    @PostMapping("/{id}/delete")
    @PreAuthorize("hasAuthority('PAGE_KNOWLEDGE_BASE')")
    public String delete(@PathVariable long id, Authentication authentication) {
        knowledgeNoteService.delete(id);
        String actor = authentication != null ? authentication.getName() : null;
        notificationRoutingService.notify("knowledge_base", "note_deleted", Set.of(), "Удалена заметка #" + id, "/knowledge-base/notes", actor);
        return "redirect:/knowledge-base/notes";
    }

    private void populateEditor(Authentication authentication, Model model, KnowledgeNoteService.NoteDetails note) {
        navigationService.enrich(model, authentication);
        model.addAttribute("note", note);
        model.addAttribute("articles", knowledgeBaseService.listArticles());
        boolean canLinkPassports = permissionService.hasAuthority(authentication, "PAGE_OBJECT_PASSPORTS");
        model.addAttribute("canLinkPassports", canLinkPassports);
        model.addAttribute("passports", canLinkPassports ? objectPassportService.listPassports() : List.of());
    }

    private KnowledgeNoteService.NoteDetails emptyNote() {
        return new KnowledgeNoteService.NoteDetails(
            null, "", "", Map.of(), Set.of(), Set.of(), "", "", null, null
        );
    }
}
