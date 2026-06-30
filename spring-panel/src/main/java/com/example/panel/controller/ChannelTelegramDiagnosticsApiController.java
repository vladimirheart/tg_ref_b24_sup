package com.example.panel.controller;

import com.example.panel.service.ChannelTransportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChannelTelegramDiagnosticsApiController {

    private final ChannelTransportService channelTransportService;

    public ChannelTelegramDiagnosticsApiController(ChannelTransportService channelTransportService) {
        this.channelTransportService = channelTransportService;
    }

    @PostMapping("/channels/{channelId}/test-message")
    public ResponseEntity<Map<String, Object>> testChannel(@PathVariable long channelId,
                                                           @RequestBody(required = false) Map<String, Object> payload) {
        return channelTransportService.testChannel(channelId, payload);
    }

    @PostMapping("/channels/{channelId}/bot-info")
    public ResponseEntity<Map<String, Object>> refreshBotInfo(@PathVariable long channelId) {
        return channelTransportService.refreshBotInfo(channelId);
    }
}
