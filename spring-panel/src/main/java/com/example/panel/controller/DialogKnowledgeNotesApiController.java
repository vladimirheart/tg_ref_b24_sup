package com.example.panel.controller;

import com.example.panel.service.KnowledgeNoteLocationService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dialogs")
@PreAuthorize("hasAuthority('PAGE_DIALOGS') and hasAuthority('PAGE_KNOWLEDGE_BASE')")
public class DialogKnowledgeNotesApiController {

    private final KnowledgeNoteLocationService knowledgeNoteLocationService;

    public DialogKnowledgeNotesApiController(KnowledgeNoteLocationService knowledgeNoteLocationService) {
        this.knowledgeNoteLocationService = knowledgeNoteLocationService;
    }

    @GetMapping("/{ticketId}/notes")
    public Map<String, Object> notes(@PathVariable String ticketId, Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : null;
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", true);
        response.put("notes", knowledgeNoteLocationService.listDialogNotes(ticketId, operator));
        return response;
    }
}
