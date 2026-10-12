package com.example.panel.controller;

import com.example.panel.service.DialogReadService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class DialogTicketQueryReadController {

    private final DialogReadService dialogReadService;

    public DialogTicketQueryReadController(DialogReadService dialogReadService) {
        this.dialogReadService = dialogReadService;
    }

    @GetMapping("/api/dialog-ticket")
    public ResponseEntity<?> details(@RequestParam("ticketId") String ticketId,
                                     @RequestParam(value = "channelId", required = false) Long channelId,
                                     Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : null;
        return dialogReadService.loadDetails(ticketId, channelId, operator);
    }

    @GetMapping("/api/dialog-ticket/history")
    public Map<String, Object> history(@RequestParam("ticketId") String ticketId,
                                       @RequestParam(value = "channelId", required = false) Long channelId,
                                       Authentication authentication) {
        String operator = authentication != null ? authentication.getName() : null;
        return dialogReadService.loadHistory(ticketId, channelId, operator);
    }

    @GetMapping("/api/dialog-ticket/history/previous")
    public ResponseEntity<?> previousHistory(@RequestParam("ticketId") String ticketId,
                                             @RequestParam(value = "offset", required = false, defaultValue = "0") Integer offset) {
        return dialogReadService.loadPreviousHistory(ticketId, offset);
    }
}
