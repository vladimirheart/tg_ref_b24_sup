package com.example.panel.controller;

import com.example.panel.service.ChannelTransportService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api")
public class ChannelApiController {

    private final ChannelTransportService channelTransportService;

    public ChannelApiController(ChannelTransportService channelTransportService) {
        this.channelTransportService = channelTransportService;
    }

    @GetMapping("/channels")
    public ResponseEntity<Map<String, Object>> getChannels() {
        return channelTransportService.getChannels();
    }

    @PostMapping("/channels")
    public ResponseEntity<Map<String, Object>> createChannel(@RequestBody(required = false) Map<String, Object> payload) {
        return channelTransportService.createChannel(payload);
    }

    @PatchMapping("/channels/{channelId}")
    public ResponseEntity<Map<String, Object>> patchChannel(@PathVariable long channelId,
                                                            @RequestBody(required = false) Map<String, Object> payload) {
        return channelTransportService.updateChannel(channelId, payload);
    }

    @PutMapping("/channels/{channelId}")
    public ResponseEntity<Map<String, Object>> putChannel(@PathVariable long channelId,
                                                          @RequestBody(required = false) Map<String, Object> payload) {
        return channelTransportService.updateChannel(channelId, payload);
    }

    @PostMapping("/channels/{channelId}")
    public ResponseEntity<Map<String, Object>> postChannel(@PathVariable long channelId,
                                                           @RequestBody(required = false) Map<String, Object> payload) {
        return channelTransportService.updateChannel(channelId, payload);
    }

    @DeleteMapping("/channels/{channelId}")
    public ResponseEntity<Map<String, Object>> deleteChannel(@PathVariable long channelId) {
        return channelTransportService.deleteChannel(channelId);
    }

    @PostMapping({"/{id}/public-id/regenerate", "/channels/{id}/public-id/regenerate"})
    public ResponseEntity<Map<String, Object>> regeneratePublicId(@PathVariable("id") long id) {
        return channelTransportService.regeneratePublicId(id);
    }
}
