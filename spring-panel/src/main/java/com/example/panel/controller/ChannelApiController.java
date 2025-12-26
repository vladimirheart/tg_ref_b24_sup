package com.example.panel.controller;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
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

    private static final Logger log = LoggerFactory.getLogger(ChannelApiController.class);

    private final ChannelRepository channelRepository;
    private final ObjectMapper objectMapper;

    public ChannelApiController(ChannelRepository channelRepository, ObjectMapper objectMapper) {
        this.channelRepository = channelRepository;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/channels")
    public ResponseEntity<Map<String, Object>> getChannels() {
        List<Channel> channels = channelRepository.findAll();
        Map<String, Object> body = new HashMap<>();
        body.put("channels", channels);
        body.put("success", true);
        log.info("Channels API returned {} channels", channels.size());
        return ResponseEntity.ok(body);
    }

    @PatchMapping("/channels/{channelId}")
    public ResponseEntity<Map<String, Object>> updateChannel(@PathVariable long channelId,
                                                             @RequestBody(required = false) Map<String, Object> payload) {
        Channel channel = channelRepository.findById(channelId).orElse(null);
        if (channel == null) {
            return ResponseEntity.status(404).body(Map.of("success", false, "error", "Канал не найден"));
        }
        Map<String, Object> data = payload != null ? payload : Collections.emptyMap();
        boolean updated = false;

        if (data.containsKey("channel_name") || data.containsKey("name")) {
            String name = stringValue(firstValue(data, "channel_name", "name"));
            if (!name.isEmpty()) {
                channel.setChannelName(name);
                updated = true;
            }
        }

        if (data.containsKey("description")) {
            channel.setDescription(stringValue(data.get("description")));
            updated = true;
        }

        if (data.containsKey("platform")) {
            String platform = stringValue(data.get("platform")).toLowerCase();
            if (!platform.isEmpty()) {
                channel.setPlatform(platform);
                updated = true;
            }
        }

        if (data.containsKey("platform_config") || data.containsKey("settings")) {
            Object raw = firstValue(data, "platform_config", "settings");
            String encoded = serializeIfNeeded(raw);
            channel.setPlatformConfig(encoded);
            updated = true;
        }

        if (data.containsKey("filters")) {
            String encoded = serializeIfNeeded(data.get("filters"));
            channel.setFilters(encoded);
            updated = true;
        }

        if (data.containsKey("is_active")) {
            channel.setActive(parseBoolean(data.get("is_active")));
            updated = true;
        }

        if (data.containsKey("support_chat_id") || data.containsKey("supportChatId")) {
            Object raw = firstValue(data, "support_chat_id", "supportChatId");
            String value = stringValue(raw);
            channel.setSupportChatId(value.isEmpty() ? null : value);
            updated = true;
        }

        if (data.containsKey("max_questions")) {
            Integer maxQuestions = parseInteger(data.get("max_questions"));
            channel.setMaxQuestions(maxQuestions);
            updated = true;
        }

        if (data.containsKey("question_template_id")) {
            channel.setQuestionTemplateId(stringValue(data.get("question_template_id")));
            updated = true;
        }

        if (data.containsKey("rating_template_id")) {
            channel.setRatingTemplateId(stringValue(data.get("rating_template_id")));
            updated = true;
        }

        if (data.containsKey("auto_action_template_id")) {
            channel.setAutoActionTemplateId(stringValue(data.get("auto_action_template_id")));
            updated = true;
        }

        if (data.containsKey("questions_cfg")) {
            String encoded = serializeIfNeeded(data.get("questions_cfg"));
            channel.setQuestionsCfg(encoded);
            updated = true;
        }

        if (data.containsKey("token")) {
            String token = stringValue(data.get("token"));
            if (!token.isEmpty()) {
                channel.setToken(token);
                updated = true;
            }
        }

        if (!updated) {
            return ResponseEntity.badRequest().body(Map.of("success", false, "error", "Нет полей для обновления"));
        }

        channelRepository.save(channel);
        return ResponseEntity.ok(Map.of("success", true, "channel", channel));
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

    private String stringValue(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private Object firstValue(Map<String, Object> data, String primaryKey, String fallbackKey) {
        Object value = data.get(primaryKey);
        return value != null ? value : data.get(fallbackKey);
    }

    private Boolean parseBoolean(Object raw) {
        if (raw instanceof Boolean bool) {
            return bool;
        }
        if (raw instanceof Number number) {
            return number.intValue() != 0;
        }
        String value = stringValue(raw).toLowerCase();
        if (value.isEmpty()) {
            return null;
        }
        return value.equals("true") || value.equals("1") || value.equals("yes") || value.equals("y");
    }

    private Integer parseInteger(Object raw) {
        if (raw instanceof Number number) {
            return number.intValue();
        }
        String value = stringValue(raw);
        if (value.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String serializeIfNeeded(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof String string) {
            return string;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Не удалось сериализовать JSON", ex);
        }
    }
}
