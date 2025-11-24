package com.example.panel.controller;

import com.example.panel.model.dialog.DialogDetails;
import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.model.dialog.DialogSummary;
import com.example.panel.service.DialogService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/dialogs")
@Validated
public class DialogApiController {

    private final DialogService dialogService;

    public DialogApiController(DialogService dialogService) {
        this.dialogService = dialogService;
    }

    @GetMapping
    public Map<String, Object> list() {
        DialogSummary summary = dialogService.loadSummary();
        List<DialogListItem> dialogs = dialogService.loadDialogs();
        Map<String, Object> payload = new HashMap<>();
        payload.put("summary", summary);
        payload.put("dialogs", dialogs);
        payload.put("success", true);
        return payload;
    }

    @GetMapping("/{ticketId}")
    public ResponseEntity<?> details(@PathVariable String ticketId,
                                      @RequestParam(value = "channelId", required = false) Long channelId) {
        Optional<DialogDetails> details = dialogService.loadDialogDetails(ticketId, channelId);
        return details.<ResponseEntity<?>>map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(Map.of("success", false, "error", "Диалог не найден")));
    }

    @GetMapping("/{ticketId}/history")
    public Map<String, Object> history(@PathVariable String ticketId,
                                        @RequestParam(value = "channelId", required = false) Long channelId) {
        return Map.of(
                "success", true,
                "messages", dialogService.loadHistory(ticketId, channelId)
        );
    }
}