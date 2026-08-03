package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.service.BotProcessService.BotProcessStatus;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsBotRuntimeMonitoringService {

    private final ChannelRepository channelRepository;
    private final BotProcessService botProcessService;

    public AnalyticsBotRuntimeMonitoringService(ChannelRepository channelRepository,
                                                BotProcessService botProcessService) {
        this.channelRepository = channelRepository;
        this.botProcessService = botProcessService;
    }

    public Map<String, Object> buildOverview() {
        List<Map<String, Object>> items = channelRepository.findAll().stream()
            .sorted(Comparator
                .comparing((Channel channel) -> !isActive(channel))
                .thenComparing(channel -> safeLower(channel.getChannelName()))
                .thenComparing(channel -> channel.getId() == null ? Long.MAX_VALUE : channel.getId()))
            .map(this::toItem)
            .toList();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", items.size());
        summary.put("active", items.stream().filter(item -> Boolean.TRUE.equals(item.get("active"))).count());
        summary.put("running", countByStatus(items, "running"));
        summary.put("stopped", countByStatus(items, "stopped"));
        summary.put("inactive", countByStatus(items, "inactive"));
        summary.put("error", countByStatus(items, "error"));

        return Map.of(
            "success", true,
            "summary", summary,
            "bots", items
        );
    }

    private long countByStatus(List<Map<String, Object>> items, String expectedStatus) {
        return items.stream()
            .filter(item -> expectedStatus.equals(item.get("status")))
            .count();
    }

    private Map<String, Object> toItem(Channel channel) {
        boolean active = isActive(channel);
        String statusCode;
        String rawStatus;
        OffsetDateTime startedAt;

        if (!active) {
            statusCode = "inactive";
            rawStatus = "inactive";
            startedAt = null;
        } else {
            BotProcessStatus processStatus = botProcessService.status(channel.getId());
            statusCode = normalizeStatusCode(processStatus);
            rawStatus = processStatus == null ? "unknown" : normalizeRawStatus(processStatus.message());
            startedAt = processStatus == null ? null : processStatus.startedAt();
        }

        Map<String, Object> item = new LinkedHashMap<>();
        item.put("channel_id", channel.getId());
        item.put("channel_name", channel.getChannelName());
        item.put("bot_name", channel.getBotName());
        item.put("bot_username", channel.getBotUsername());
        item.put("platform", safeLower(channel.getPlatform()));
        item.put("active", active);
        item.put("status", statusCode);
        item.put("raw_status", rawStatus);
        item.put("started_at", startedAt);
        return item;
    }

    private String normalizeStatusCode(BotProcessStatus processStatus) {
        if (processStatus == null) {
            return "error";
        }
        String normalized = normalizeRawStatus(processStatus.message());
        if ("running".equals(normalized)) {
            return "running";
        }
        if ("stopped".equals(normalized)) {
            return "stopped";
        }
        return "error";
    }

    private boolean isActive(Channel channel) {
        return channel != null && Boolean.TRUE.equals(channel.getActive());
    }

    private String normalizeRawStatus(String value) {
        String normalized = safeLower(value);
        if (normalized.isBlank()) {
            return "unknown";
        }
        return normalized;
    }

    private String safeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
