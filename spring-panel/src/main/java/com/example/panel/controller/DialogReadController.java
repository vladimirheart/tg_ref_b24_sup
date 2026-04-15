package com.example.panel.controller;

import com.example.panel.service.DialogReadService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/dialogs")
public class DialogReadController {

    private final DialogReadService dialogReadService;

    public DialogReadController(DialogReadService dialogReadService) {
        this.dialogReadService = dialogReadService;
    }

    @GetMapping("/public-form-metrics")
    public Map<String, Object> publicFormMetrics(@RequestParam(value = "channelId", required = false) Long channelId) {
        return dialogReadService.loadPublicFormMetrics(channelId);
    }

    @GetMapping("/{ticketId}")
    public ResponseEntity<?> details(@PathVariable String ticketId,
                                     @RequestParam(value = "channelId", required = false) Long channelId,
                                     Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : null;
        return dialogReadService.loadDetails(ticketId, channelId, operator);
    }

    @GetMapping("/{ticketId}/history")
    public Map<String, Object> history(@PathVariable String ticketId,
                                       @RequestParam(value = "channelId", required = false) Long channelId,
                                       Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : null;
        return dialogReadService.loadHistory(ticketId, channelId, operator);
    }

    @GetMapping("/{ticketId}/history/previous")
    public ResponseEntity<?> previousHistory(@PathVariable String ticketId,
                                             @RequestParam(value = "offset", required = false, defaultValue = "0") Integer offset) {
        return dialogReadService.loadPreviousHistory(ticketId, offset);
    }
}
