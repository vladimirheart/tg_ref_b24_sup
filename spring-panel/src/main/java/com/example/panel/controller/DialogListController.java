package com.example.panel.controller;

import com.example.panel.service.DialogListReadService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/dialogs")
public class DialogListController {

    private final DialogListReadService dialogListReadService;

    public DialogListController(DialogListReadService dialogListReadService) {
        this.dialogListReadService = dialogListReadService;
    }

    @GetMapping
    public Map<String, Object> list(Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : null;
        return dialogListReadService.loadListPayload(operator);
    }
}
