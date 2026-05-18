package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.model.publicform.PublicFormConfig;
import com.example.panel.model.publicform.PublicFormQuestion;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Service
public class PublicFormDefinitionService {
    private static final Logger log = LoggerFactory.getLogger(PublicFormDefinitionService.class);
    private static final Set<String> LOCATION_FIELD_IDS = Set.of("business", "location_type", "city", "location_name");

    private final ObjectMapper objectMapper;
    private final PublicFormRuntimeConfigService runtimeConfigService;
    private final SettingsCatalogService settingsCatalogService;
    private final IikoDepartmentLocationCatalogService locationCatalogService;

    public PublicFormDefinitionService(ObjectMapper objectMapper,
                                       PublicFormRuntimeConfigService runtimeConfigService,
                                       SettingsCatalogService settingsCatalogService,
                                       IikoDepartmentLocationCatalogService locationCatalogService) {
        this.objectMapper = objectMapper;
        this.runtimeConfigService = runtimeConfigService;
        this.settingsCatalogService = settingsCatalogService;
        this.locationCatalogService = locationCatalogService;
    }

    public PublicFormConfig buildConfig(Channel channel) {
        List<PublicFormQuestion> questions = parseQuestions(channel);
        String publicId = StringUtils.hasText(channel.getPublicId())
                ? channel.getPublicId()
                : String.valueOf(channel.getId());
        ParsedPublicFormSettings settings = parseSettings(channel);
        return new PublicFormConfig(channel.getId(), publicId, channel.getChannelName(), settings.schemaVersion(), settings.enabled(),
                settings.captchaEnabled(), settings.disabledStatus(), settings.successInstruction(),
                settings.responseEtaMinutes(), questions);
    }

    public PublicFormConfig buildDemoConfig() {
        List<PublicFormQuestion> demoQuestions = List.of(
                new PublicFormQuestion("client_name", "Как вас зовут?", "text", 1, Map.of("required", true)),
                new PublicFormQuestion("contact", "Как с вами связаться?", "text", 2, Map.of("required", true)),
                new PublicFormQuestion("urgency", "Насколько срочно решить вопрос?", "select", 3, Map.of(
                        "required", true,
                        "options", List.of("Срочно", "В течение дня", "Не горит"),
                        "placeholder", "Выберите приоритет"
                )),
                new PublicFormQuestion("location", "Где возникла проблема?", "text", 4, Map.of("placeholder", "Адрес или подразделение")),
                new PublicFormQuestion("details", "Опишите ситуацию подробнее", "textarea", 5, Map.of("rows", 3, "maxLength", 1000))
        );

        return new PublicFormConfig(0L, "demo", "Демо-канал", 1, true, false, 404,
                "Обычно отвечаем в течение рабочего дня.", 240, demoQuestions);
    }

    private List<PublicFormQuestion> parseQuestions(Channel channel) {
        ParsedPublicFormSettings settings = parseSettings(channel);
        if (settings.fields().isEmpty()) {
            return List.of();
        }
        Map<String, Map<String, Object>> locationFields = loadLocationPresetFields();
        AtomicInteger index = new AtomicInteger(0);
        return settings.fields().stream()
                .map(entry -> normalizeQuestion(entry, index.incrementAndGet()))
                .map(question -> enrichLocationQuestion(question, locationFields))
                .sorted(Comparator.comparingInt(question -> Optional.ofNullable(question.order()).orElse(0)))
                .toList();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Map<String, Object>> loadLocationPresetFields() {
        try {
            IikoDepartmentLocationCatalogService.LocationCatalogSnapshot catalog = locationCatalogService.loadCatalog();
            Map<String, Object> presets = settingsCatalogService.buildLocationPresets(catalog.tree(), catalog.statuses());
            Object locationsGroup = presets.get("locations");
            if (!(locationsGroup instanceof Map<?, ?> groupMap)) {
                return Map.of();
            }
            Object fieldsRaw = groupMap.get("fields");
            if (!(fieldsRaw instanceof Map<?, ?> fieldsMap)) {
                return Map.of();
            }
            LinkedHashMap<String, Map<String, Object>> result = new LinkedHashMap<>();
            fieldsMap.forEach((key, value) -> {
                if (key != null && value instanceof Map<?, ?> fieldMap) {
                    LinkedHashMap<String, Object> metadata = new LinkedHashMap<>();
                    fieldMap.forEach((metaKey, metaValue) -> {
                        if (metaKey != null) {
                            metadata.put(String.valueOf(metaKey), metaValue);
                        }
                    });
                    result.put(String.valueOf(key), metadata);
                }
            });
            return result;
        } catch (Exception ex) {
            log.warn("Failed to load location presets for public form: {}", ex.getMessage());
            return Map.of();
        }
    }

    private PublicFormQuestion enrichLocationQuestion(PublicFormQuestion question, Map<String, Map<String, Object>> locationFields) {
        if (question == null || !LOCATION_FIELD_IDS.contains(question.id())) {
            return question;
        }
        Map<String, Object> presetMetadata = locationFields.get(question.id());
        if (presetMetadata == null || presetMetadata.isEmpty()) {
            return question;
        }
        LinkedHashMap<String, Object> metadata = new LinkedHashMap<>(Optional.ofNullable(question.metadata()).orElse(Map.of()));
        if (presetMetadata.containsKey("options")) {
            metadata.put("options", presetMetadata.get("options"));
        }
        if (presetMetadata.containsKey("tree")) {
            metadata.put("tree", presetMetadata.get("tree"));
        } else {
            metadata.remove("tree");
        }
        if (presetMetadata.containsKey("option_dependencies")) {
            metadata.put("option_dependencies", presetMetadata.get("option_dependencies"));
        } else {
            metadata.remove("option_dependencies");
        }
        if (!metadata.containsKey("placeholder")) {
            metadata.put("placeholder", "Выберите вариант");
        }
        return new PublicFormQuestion(question.id(), question.text(), "select", question.order(), metadata);
    }

    private ParsedPublicFormSettings parseSettings(Channel channel) {
        String payload = channel.getQuestionsCfg();
        if (!StringUtils.hasText(payload)) {
            return ParsedPublicFormSettings.defaults();
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root.isArray()) {
                List<Map<String, Object>> fields = objectMapper.convertValue(root, new TypeReference<List<Map<String, Object>>>() {
                });
                return new ParsedPublicFormSettings(1, true, false, 404,
                        null, null, null, fields, null, null);
            }
            if (root.isObject()) {
                int schemaVersion = Math.max(1, root.path("schemaVersion").asInt(1));
                boolean enabled = !root.has("enabled") || root.path("enabled").asBoolean(true);
                boolean captchaEnabled = root.path("captchaEnabled").asBoolean(false);
                int disabledStatus = runtimeConfigService.normalizeDisabledStatus(root.path("disabledStatus").asInt(404));
                JsonNode fieldsNode = root.path("fields");
                List<Map<String, Object>> fields = fieldsNode.isArray()
                        ? objectMapper.convertValue(fieldsNode, new TypeReference<List<Map<String, Object>>>() {
                        })
                        : List.of();
                Boolean rateLimitEnabled = root.has("rateLimitEnabled")
                        ? root.path("rateLimitEnabled").asBoolean(false)
                        : null;
                Integer rateLimitWindowSeconds = root.has("rateLimitWindowSeconds")
                        ? normalizeRange(root.path("rateLimitWindowSeconds").asInt(60), 10, 3600)
                        : null;
                Integer rateLimitMaxRequests = root.has("rateLimitMaxRequests")
                        ? normalizeRange(root.path("rateLimitMaxRequests").asInt(5), 1, 500)
                        : null;
                String successInstruction = trim(value(root.get("successInstruction")));
                Integer responseEtaMinutes = root.has("responseEtaMinutes")
                        ? normalizeRange(root.path("responseEtaMinutes").asInt(0), 0, 7 * 24 * 60)
                        : null;
                return new ParsedPublicFormSettings(schemaVersion, enabled, captchaEnabled, disabledStatus,
                        rateLimitEnabled, rateLimitWindowSeconds, rateLimitMaxRequests,
                        fields, successInstruction, responseEtaMinutes);
            }
            return ParsedPublicFormSettings.defaults();
        } catch (Exception ex) {
            log.warn("Failed to parse questions configuration for channel {}: {}", channel.getId(), ex.getMessage());
            return ParsedPublicFormSettings.defaults();
        }
    }

    private PublicFormQuestion normalizeQuestion(Map<String, Object> raw, int index) {
        String id = value(raw.getOrDefault("id", "q" + index));
        String text = value(raw.get("text"));
        String type = value(raw.getOrDefault("type", "text"));
        Integer order = raw.get("order") instanceof Number number ? number.intValue() : index;
        Map<String, Object> metadata = raw.entrySet().stream()
                .filter(entry -> !List.of("id", "text", "type", "order").contains(entry.getKey()))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b, LinkedHashMap::new));
        return new PublicFormQuestion(id, text, type, order, metadata);
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String value(Object value) {
        if (value instanceof JsonNode node) {
            return node.isTextual() ? node.asText() : node.toString();
        }
        return value != null ? value.toString() : null;
    }

    private int normalizeRange(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private record ParsedPublicFormSettings(int schemaVersion,
                                            boolean enabled,
                                            boolean captchaEnabled,
                                            int disabledStatus,
                                            Boolean rateLimitEnabled,
                                            Integer rateLimitWindowSeconds,
                                            Integer rateLimitMaxRequests,
                                            List<Map<String, Object>> fields,
                                            String successInstruction,
                                            Integer responseEtaMinutes) {
        private static ParsedPublicFormSettings defaults() {
            return new ParsedPublicFormSettings(1, true, false, 404,
                    null, null, null, List.of(), null, null);
        }
    }
}
