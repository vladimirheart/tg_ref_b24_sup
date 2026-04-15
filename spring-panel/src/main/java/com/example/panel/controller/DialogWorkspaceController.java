package com.example.panel.controller;

import com.example.panel.service.DialogWorkspaceService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dialogs")
@Validated
public class DialogWorkspaceController {

    private final DialogWorkspaceService dialogWorkspaceService;

    public DialogWorkspaceController(DialogWorkspaceService dialogWorkspaceService) {
        this.dialogWorkspaceService = dialogWorkspaceService;
    }

    @GetMapping("/{ticketId}/workspace")
    public ResponseEntity<?> workspace(@PathVariable String ticketId,
                                       @RequestParam(value = "channelId", required = false) Long channelId,
                                       @RequestParam(value = "include", required = false) String include,
                                       @RequestParam(value = "limit", required = false) Integer limit,
                                       @RequestParam(value = "cursor", required = false) String cursor,
                                       Authentication authentication) {
        return dialogWorkspaceService.workspace(ticketId, channelId, include, limit, cursor, authentication);
    }
}
