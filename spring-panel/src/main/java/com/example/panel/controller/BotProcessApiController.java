package com.example.panel.controller;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.service.BotProcessService;
import com.example.panel.service.BotProcessService.BotProcessStatus;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bots")
public class BotProcessApiController {

    private final BotProcessService botProcessService;
    private final ChannelRepository channelRepository;

    public BotProcessApiController(BotProcessService botProcessService,
                                   ChannelRepository channelRepository) {
        this.botProcessService = botProcessService;
        this.channelRepository = channelRepository;
    }

    @PostMapping("/{channelId}/start")
    public ResponseEntity<Map<String, Object>> start(@PathVariable Long channelId) {
        Channel channel = channelRepository.findById(channelId).orElse(null);
        if (channel == null) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "Канал не найден"));
        }
        BotProcessStatus status = botProcessService.start(channel);
        return ResponseEntity.ok(Map.of("success", status.running(), "status", status.message(), "startedAt", status.startedAt()));
    }

    @PostMapping("/{channelId}/stop")
    public ResponseEntity<Map<String, Object>> stop(@PathVariable Long channelId) {
        BotProcessStatus status = botProcessService.stop(channelId);
        return ResponseEntity.ok(Map.of("success", true, "status", status.message()));
    }

    @GetMapping("/{channelId}/status")
    public ResponseEntity<Map<String, Object>> status(@PathVariable Long channelId) {
        BotProcessStatus status = botProcessService.status(channelId);
        return ResponseEntity.ok(Map.of("success", true, "status", status.message(), "startedAt", status.startedAt()));
    }
}
