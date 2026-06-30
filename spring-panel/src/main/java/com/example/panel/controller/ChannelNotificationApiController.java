package com.example.panel.controller;

import com.example.panel.service.ChannelTransportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChannelNotificationApiController {

    private final ChannelTransportService channelTransportService;

    public ChannelNotificationApiController(ChannelTransportService channelTransportService) {
        this.channelTransportService = channelTransportService;
    }

    @GetMapping("/channel-notifications")
    public ResponseEntity<Map<String, Object>> getChannelNotifications() {
        return channelTransportService.getChannelNotifications();
    }
}
