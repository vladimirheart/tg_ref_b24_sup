package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BotRuntimeConfigService {

    private final BotRuntimeChannelService channelService;
    private final SharedConfigService sharedConfigService;
    private final BotSettingsPayloadNormalizer botSettingsPayloadNormalizer;
    private final ObjectMapper objectMapper;

    public BotRuntimeConfigService(BotRuntimeChannelService channelService,
                                   SharedConfigService sharedConfigService,
                                   BotSettingsPayloadNormalizer botSettingsPayloadNormalizer,
                                   ObjectMapper objectMapper) {
        this.channelService = channelService;
        this.sharedConfigService = sharedConfigService;
        this.botSettingsPayloadNormalizer = botSettingsPayloadNormalizer;
        this.objectMapper = objectMapper;
    }

    public Optional<RuntimeConfigLookup> findRuntimeConfig(Long channelId) {
        return channelService.findChannel(channelId)
            .map(channel -> new RuntimeConfigLookup(
                channel.getId(),
                resolveBotSettings(channel),
                resolveLocationTree(),
                sharedConfigService.presetDefinitions()
            ));
    }

    private Map<String, Object> resolveBotSettings(Channel channel) {
        Map<String, Object> settings = new LinkedHashMap<>(sharedConfigService.loadSettings());
        Map<String, Object> botSettings = botSettingsPayloadNormalizer.normalize(settings.get("bot_settings"));
        applyChannelTemplateSelection(botSettings, channel);
        return botSettings;
    }

    private Map<String, Object> resolveLocationTree() {
        JsonNode locations = sharedConfigService.loadLocations();
        if (locations == null || locations.isNull()) {
            return new LinkedHashMap<>();
        }
        JsonNode treeNode = locations.get("tree");
        Map<String, Object> resolved = objectMapper.convertValue(
            treeNode != null && !treeNode.isNull() ? treeNode : locations,
            new TypeReference<>() {}
        );
        return resolved != null ? resolved : new LinkedHashMap<>();
    }

    private void applyChannelTemplateSelection(Map<String, Object> botSettings, Channel channel) {
        if (botSettings == null || botSettings.isEmpty() || channel == null) {
            return;
        }
        List<Map<String, Object>> questionTemplates = castTemplateList(botSettings.get("question_templates"));
        String questionTemplateId = stringValue(channel.getQuestionTemplateId());
        if (StringUtils.hasText(questionTemplateId) && findTemplateById(questionTemplates, questionTemplateId) != null) {
            botSettings.put("active_template_id", questionTemplateId);
        }

        List<Map<String, Object>> ratingTemplates = castTemplateList(botSettings.get("rating_templates"));
        String ratingTemplateId = stringValue(channel.getRatingTemplateId());
        if (StringUtils.hasText(ratingTemplateId) && findTemplateById(ratingTemplates, ratingTemplateId) != null) {
            botSettings.put("active_rating_template_id", ratingTemplateId);
        }
    }

    private List<Map<String, Object>> castTemplateList(Object rawTemplates) {
        List<Map<String, Object>> templates = new ArrayList<>();
        if (!(rawTemplates instanceof List<?> list)) {
            return templates;
        }
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> template = new LinkedHashMap<>();
            map.forEach((key, value) -> {
                if (key != null) {
                    template.put(key.toString(), value);
                }
            });
            templates.add(template);
        }
        return templates;
    }

    private Map<String, Object> findTemplateById(List<Map<String, Object>> templates, String templateId) {
        if (!StringUtils.hasText(templateId)) {
            return null;
        }
        for (Map<String, Object> template : templates) {
            if (templateId.equals(stringValue(template.get("id")))) {
                return template;
            }
        }
        return null;
    }

    private String stringValue(Object rawValue) {
        return rawValue != null ? rawValue.toString().trim() : "";
    }

    public record RuntimeConfigLookup(Long channelId,
                                      Map<String, Object> botSettings,
                                      Map<String, Object> locationTree,
                                      Map<String, Object> presetDefinitions) {
    }
}
