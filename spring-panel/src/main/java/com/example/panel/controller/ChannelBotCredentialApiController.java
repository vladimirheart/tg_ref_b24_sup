package com.example.panel.controller;

import com.example.panel.service.ChannelTransportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChannelBotCredentialApiController {

    private final ChannelTransportService channelTransportService;

    public ChannelBotCredentialApiController(ChannelTransportService channelTransportService) {
        this.channelTransportService = channelTransportService;
    }

    @GetMapping("/bot-credentials")
    public ResponseEntity<Map<String, Object>> getBotCredentials() {
        return channelTransportService.getBotCredentials();
    }

    @PostMapping("/bot-credentials")
    public ResponseEntity<Map<String, Object>> createBotCredential(@RequestBody(required = false) Map<String, Object> payload) {
        return channelTransportService.createBotCredential(payload);
    }

    @DeleteMapping("/bot-credentials/{credentialId}")
    public ResponseEntity<Map<String, Object>> deleteBotCredential(@PathVariable long credentialId) {
        return channelTransportService.deleteBotCredential(credentialId);
    }
}
