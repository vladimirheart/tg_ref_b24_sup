package com.example.panel.controller;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChannelApiController {

    private final ChannelRepository channelRepository;

    public ChannelApiController(ChannelRepository channelRepository) {
        this.channelRepository = channelRepository;
    }

    @GetMapping("/channels")
    public ResponseEntity<Map<String, Object>> getChannels() {
        List<Channel> channels = channelRepository.findAll();
        Map<String, Object> body = new HashMap<>();
        body.put("channels", channels);
        body.put("success", true);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/bot-credentials")
    public ResponseEntity<Map<String, Object>> getBotCredentials() {
        Map<String, Object> body = new HashMap<>();
        body.put("credentials", Collections.emptyList());
        body.put("success", true);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/channel-notifications")
    public ResponseEntity<Map<String, Object>> getChannelNotifications() {
        Map<String, Object> body = new HashMap<>();
        body.put("notifications", Collections.emptyList());
        body.put("success", true);
        return ResponseEntity.ok(body);
    }
}
