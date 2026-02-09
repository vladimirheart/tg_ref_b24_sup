package com.example.supportbot.settings;

import com.example.supportbot.entity.Channel;
import com.example.supportbot.settings.dto.BotSettingsDto;
import com.example.supportbot.service.SharedConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * Service that sanitises raw settings received from the admin panel and exposes
 * helpers for rating scale management and location preset generation.
 */

@Service
public class BotSettingsService {

    private final ObjectMapper objectMapper;
    private final SharedConfigService sharedConfigService;

    public BotSettingsService(ObjectMapper objectMapper, SharedConfigService sharedConfigService) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.sharedConfigService = Objects.requireNonNull(sharedConfigService, "sharedConfigService");
    }

    public BotSettingsDto buildDefaultSettings() {
        Map<String, Object> presetDefinitions = sharedConfigService.presetDefinitions();
        Map<String, Object> sharedDefaults = sharedConfigService.loadSettings();
        if (!sharedDefaults.isEmpty()) {
            Map<String, Object> sanitized = sanitizeBotSettingsInternal(sharedDefaults, presetDefinitions, 10);
            return objectMapper.convertValue(sanitized, BotSettingsDto.class);
        }
        Map<String, Object> defaults = defaultBotSettingsInternal(presetDefinitions);
        return objectMapper.convertValue(defaults, BotSettingsDto.class);
    }

    public BotSettingsDto buildDefaultSettings(Map<String, Object> presetDefinitions) {
        Map<String, Object> defaults = defaultBotSettingsInternal(presetDefinitions);
        return objectMapper.convertValue(defaults, BotSettingsDto.class);
    }

    public BotSettingsDto sanitizeFromJson(Object rawJson) {
        return sanitizeFromJson(rawJson, sharedConfigService.presetDefinitions(), 10);
    }

    public BotSettingsDto sanitizeFromJson(Object rawJson, Map<String, Object> presetDefinitions, int maxScale) {
        Map<String, Object> raw = convertToMap(rawJson);
        Map<String, Object> sanitized = sanitizeBotSettingsInternal(raw, presetDefinitions, maxScale);
        return objectMapper.convertValue(sanitized, BotSettingsDto.class);
    }

    /**
     * Build settings from a raw {@link Channel#getQuestionsCfg()} JSON payload.
     * Falls back to defaults if the config is missing or invalid.
     */
    public BotSettingsDto loadFromChannel(Channel channel) {
        if (channel == null || channel.getQuestionsCfg() == null) {
            return buildDefaultSettings();
        }
        Map<String, Object> raw = convertToMap(channel.getQuestionsCfg());
        Map<String, Object> sanitized = sanitizeBotSettingsInternal(raw,
                sharedConfigService.presetDefinitions(), 10);
        return objectMapper.convertValue(sanitized, BotSettingsDto.class);
    }

    public Map<String, Object> buildLocationPresets(Map<String, Object> locationTree) {
        return buildLocationPresets(locationTree, sharedConfigService.presetDefinitions());
    }

    public Map<String, Object> buildLocationPresets(Map<String, Object> locationTree, Map<String, Object> baseDefinitions) {
        return buildLocationPresetsInternal(locationTree, baseDefinitions);
    }

    public int ratingScale(BotSettingsDto settings, int defaultValue) {
        if (settings == null || settings.getRatingSystem() == null) {
            return defaultValue;
        }
        int scale = settings.getRatingSystem().getScaleSize();
        return scale >= 1 ? scale : defaultValue;
    }

    public Set<String> ratingAllowedValues(BotSettingsDto settings) {
        int scale = ratingScale(settings, 5);
        Set<String> values = new LinkedHashSet<>();
        for (int i = 1; i <= scale; i++) {
            values.add(Integer.toString(i));
        }
        return values;
    }

    public String ratingPrompt(BotSettingsDto settings, String defaultPrompt) {
        if (settings == null || settings.getRatingSystem() == null) {
            return defaultPrompt != null ? defaultPrompt : defaultRatingPrompt(5);
        }
        String prompt = settings.getRatingSystem().getPromptText();
        if (prompt != null && !prompt.isBlank()) {
            return prompt.trim();
        }
        return defaultPrompt != null ? defaultPrompt : defaultRatingPrompt(ratingScale(settings, 5));
    }

    public Map<String, String> ratingResponses(BotSettingsDto settings) {
        if (settings == null || settings.getRatingSystem() == null) {
            return Collections.emptyMap();
        }
        List<com.example.supportbot.settings.dto.RatingResponseDto> responses = settings.getRatingSystem().getResponses();
        if (responses == null) {
            return Collections.emptyMap();
        }
        Map<String, String> mapped = new LinkedHashMap<>();
        for (com.example.supportbot.settings.dto.RatingResponseDto response : responses) {
            if (response == null) {
                continue;
            }
            String text = response.getText();
            if (text == null || text.isBlank()) {
                continue;
            }
            mapped.put(Integer.toString(response.getValue()), text.trim());
        }
        return mapped;
    }

    public Optional<String> ratingResponseFor(BotSettingsDto settings, Object value) {
        if (value == null) {
            return Optional.empty();
        }
        String key = value.toString();
        return Optional.ofNullable(ratingResponses(settings).get(key));
    }

    // Internal helpers for settings sanitization ----------------------------

    private Map<String, Object> convertToMap(Object raw) {
        if (raw instanceof String json && !json.isBlank()) {
            try {
                return objectMapper.readValue(json, LinkedHashMap.class);
            } catch (Exception ignored) {
                // fallback to default conversion below
            }
        }
        if (raw instanceof Map<?, ?> map) {
            Map<String, Object> copy = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (entry.getKey() != null) {
                    copy.put(entry.getKey().toString(), entry.getValue());
                }
            }
            return copy;
        }
        if (raw == null) {
            return new LinkedHashMap<>();
        }
        return objectMapper.convertValue(raw, LinkedHashMap.class);
    }

    private Map<String, Object> defaultBotSettingsInternal(Map<String, Object> definitions) {
        Map<PresetKey, Map<String, Object>> lookup = preparePresetLookup(definitions);

        String defaultTemplateId = "template-default";
        List<Map<String, Object>> defaultQuestions = new ArrayList<>();
        int order = 1;
        Object[][] defaults = new Object[][]{
            {"locations", "business", "Выберите бизнес"},
            {"locations", "location_type", "Выберите тип бизнеса"},
            {"locations", "city", "Выберите город"},
            {"locations", "location_name", "Укажите локацию"}
        };
        for (Object[] triple : defaults) {
            String group = triple[0].toString();
            String field = triple[1].toString();
            String fallback = triple[2].toString();
            Map<String, Object> meta = lookup.getOrDefault(new PresetKey(group, field), Collections.emptyMap());
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", field);
            entry.put("type", "preset");
            entry.put("text", meta.getOrDefault("label", fallback));
            entry.put("preset", Map.of("group", group, "field", field));
            entry.put("order", order);
            defaultQuestions.add(entry);
            order += 1;
        }

        int defaultScale = 5;
        String ratingPrompt = defaultRatingPrompt(defaultScale);
        String defaultRatingTemplateId = "rating-template-default";
        Map<String, Object> defaultRatingTemplate = new LinkedHashMap<>();
        defaultRatingTemplate.put("id", defaultRatingTemplateId);
        defaultRatingTemplate.put("name", "Базовый сценарий оценок");
        defaultRatingTemplate.put("prompt_text", ratingPrompt);
        defaultRatingTemplate.put("scale_size", defaultScale);
        defaultRatingTemplate.put("responses", buildDefaultResponses(defaultScale));

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("question_templates", List.of(Map.of(
                "id", defaultTemplateId,
                "name", "Основной шаблон вопросов",
                "question_flow", defaultQuestions)));
        settings.put("active_template_id", defaultTemplateId);
        settings.put("rating_templates", List.of(defaultRatingTemplate));
        settings.put("active_rating_template_id", defaultRatingTemplateId);
        settings.put("rating_system", Map.of(
                "prompt_text", ratingPrompt,
                "scale_size", defaultScale,
                "responses", buildDefaultResponses(defaultScale)));
        settings.put("question_flow", defaultQuestions);
        settings.put("unblock_request_cooldown_minutes", 60);
        return settings;
    }

    private Map<PresetKey, Map<String, Object>> preparePresetLookup(Map<String, Object> definitions) {
        Map<PresetKey, Map<String, Object>> lookup = new LinkedHashMap<>();
        if (definitions == null) {
            return lookup;
        }
        for (Map.Entry<String, Object> groupEntry : definitions.entrySet()) {
            if (!(groupEntry.getValue() instanceof Map<?, ?> groupData)) {
                continue;
            }
            Object fieldsObj = groupData.get("fields");
            if (!(fieldsObj instanceof Map<?, ?> fields)) {
                continue;
            }
            for (Map.Entry<?, ?> fieldEntry : fields.entrySet()) {
                String fieldKey = fieldEntry.getKey().toString();
                Map<String, Object> meta = new LinkedHashMap<>();
                String label = fieldKey.replace("_", " ").trim();
                List<String> options = new ArrayList<>();
                if (fieldEntry.getValue() instanceof Map<?, ?> metaMap) {
                    Object labelObj = metaMap.get("label");
                    if (labelObj instanceof String labelStr && !labelStr.isBlank()) {
                        label = labelStr.trim();
                    }
                    Object optionsObj = metaMap.get("options");
                    if (optionsObj instanceof Collection<?> rawOptions) {
                        for (Object option : rawOptions) {
                            if (option instanceof String optionStr && !optionStr.isBlank()) {
                                options.add(optionStr.trim());
                            }
                        }
                    }
                }
                meta.put("label", label.isBlank() ? capitalize(fieldKey) : label);
                meta.put("options", options);
                lookup.put(new PresetKey(groupEntry.getKey(), fieldKey), meta);
            }
        }
        return lookup;
    }

    private Map<String, Object> sanitizeBotSettingsInternal(
            Map<String, Object> raw,
            Map<String, Object> definitions,
            int maxScale
    ) {
        Map<String, Object> defaults = defaultBotSettingsInternal(definitions);
        if (raw == null || raw.isEmpty()) {
            return defaults;
        }
        Map<PresetKey, Map<String, Object>> lookup = preparePresetLookup(definitions);
        Set<PresetKey> allowedPresets = lookup.keySet();

        List<Map<String, Object>> templates = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        Object rawTemplatesObj = raw.get("question_templates");
        if (rawTemplatesObj instanceof Iterable<?> iterable && !(rawTemplatesObj instanceof String)) {
            for (Object item : iterable) {
                if (!(item instanceof Map<?, ?> templateMap)) {
                    continue;
                }
                String templateId = ensureUuid(templateMap.get("id"));
                if (seenIds.contains(templateId)) {
                    templateId = ensureUuid(null);
                }
                seenIds.add(templateId);
                String name = optionalString(templateMap.get("name"));
                if (name.isBlank()) {
                    name = "Шаблон вопросов";
                }
                String description = optionalString(templateMap.get("description"));
                Object flowSource = templateMap.get("question_flow");
                if (!(flowSource instanceof Iterable<?>) || flowSource instanceof String) {
                    flowSource = templateMap.get("questions");
                }
                List<Map<String, Object>> sanitizedFlow = sanitizeQuestionFlow(
                        flowSource,
                        allowedPresets,
                        lookup
                );
                if (sanitizedFlow.isEmpty()) {
                    continue;
                }
                Map<String, Object> templateEntry = new LinkedHashMap<>();
                templateEntry.put("id", templateId);
                templateEntry.put("name", name.isBlank() ? "Шаблон вопросов" : name);
                templateEntry.put("question_flow", sanitizedFlow);
                if (!description.isBlank()) {
                    templateEntry.put("description", description);
                }
                templates.add(templateEntry);
            }
        }

        List<Map<String, Object>> fallbackFlow = sanitizeQuestionFlow(
                raw.get("question_flow"),
                allowedPresets,
                lookup
        );
        if (!fallbackFlow.isEmpty() && templates.isEmpty()) {
            Map<String, Object> imported = new LinkedHashMap<>();
            imported.put("id", defaultsValue(defaults, "question_templates", 0, "id"));
            imported.put("name", "Импортированный сценарий");
            imported.put("question_flow", fallbackFlow);
            templates.add(imported);
        }
        if (templates.isEmpty()) {
            templates.addAll(castList(defaults.get("question_templates")));
        }

        String activeTemplateId = optionalString(raw.get("active_template_id"));
        if (activeTemplateId.isBlank()) {
            activeTemplateId = (String) templates.get(0).get("id");
        }
        Set<String> templateIds = templates.stream().map(t -> t.get("id").toString()).collect(Collectors.toSet());
        if (!templateIds.contains(activeTemplateId)) {
            activeTemplateId = templates.get(0).get("id").toString();
        }

        List<Map<String, Object>> ratingTemplates = new ArrayList<>();
        Set<String> seenRatingIds = new HashSet<>();
        Object rawRatingTemplatesObj = raw.get("rating_templates");
        if (rawRatingTemplatesObj instanceof Iterable<?> ratingIterable && !(rawRatingTemplatesObj instanceof String)) {
            for (Object item : ratingIterable) {
                if (!(item instanceof Map<?, ?> templateMap)) {
                    continue;
                }
                String templateId = ensureUuid(templateMap.get("id"));
                if (seenRatingIds.contains(templateId)) {
                    templateId = ensureUuid(null);
                }
                seenRatingIds.add(templateId);
                String name = optionalString(templateMap.get("name"));
                if (name.isBlank()) {
                    name = "Шаблон оценок";
                }
                String description = optionalString(templateMap.get("description"));
                int scale = normalizeScale(templateMap.get("scale_size"), templateMap.get("scale"), templateMap.get("scaleSize"), defaults, maxScale);
                String prompt = optionalString(templateMap.get("prompt_text"));
                if (prompt.isBlank()) {
                    prompt = optionalString(templateMap.get("prompt"));
                }
                if (prompt.isBlank()) {
                    prompt = optionalString(templateMap.get("promptText"));
                }
                if (prompt.isBlank()) {
                    prompt = defaultRatingPrompt(scale);
                }
                List<Map<String, Object>> responses = sanitizeRatingResponses(
                        templateMap.get("responses"),
                        scale,
                        buildDefaultResponses(scale)
                );
                Map<String, Object> templateEntry = new LinkedHashMap<>();
                templateEntry.put("id", templateId);
                templateEntry.put("name", name);
                templateEntry.put("prompt_text", prompt);
                templateEntry.put("scale_size", scale);
                templateEntry.put("responses", responses);
                if (!description.isBlank()) {
                    templateEntry.put("description", description);
                }
                ratingTemplates.add(templateEntry);
            }
        }

        List<Map<String, Object>> ratingDefaults = castList(defaults.get("rating_templates"));
        Object rawRatingSystem = raw.get("rating_system");
        Map<String, Object> ratingSystem = rawRatingSystem instanceof Map<?, ?> ? convertToMap(rawRatingSystem) : new LinkedHashMap<>();
        if (ratingTemplates.isEmpty() && !ratingSystem.isEmpty()) {
            Map<String, Object> entry = new LinkedHashMap<>();
            String templateId = ensureUuid(ratingSystem.get("id"));
            entry.put("id", templateId);
            String name = optionalString(ratingSystem.get("name"));
            entry.put("name", name.isBlank() ? "Шаблон оценок" : name);
            String description = optionalString(ratingSystem.get("description"));
            int scale = normalizeScale(ratingSystem.get("scale_size"), ratingSystem.get("scale"), ratingSystem.get("scaleSize"), defaults, maxScale);
            String prompt = optionalString(ratingSystem.get("prompt_text"));
            if (prompt.isBlank()) {
                prompt = optionalString(ratingSystem.get("prompt"));
            }
            if (prompt.isBlank()) {
                prompt = defaultRatingPrompt(scale);
            }
            List<Map<String, Object>> responses = sanitizeRatingResponses(
                    ratingSystem.get("responses"),
                    scale,
                    buildDefaultResponses(scale)
            );
            entry.put("prompt_text", prompt);
            entry.put("scale_size", scale);
            entry.put("responses", responses);
            if (!description.isBlank()) {
                entry.put("description", description);
            }
            ratingTemplates.add(entry);
        }
        if (ratingTemplates.isEmpty()) {
            ratingTemplates.addAll(ratingDefaults);
        }

        String activeRatingTemplateId = optionalString(raw.get("active_rating_template_id"));
        if (activeRatingTemplateId.isBlank()) {
            activeRatingTemplateId = ratingTemplates.get(0).get("id").toString();
        }
        Set<String> ratingIds = ratingTemplates.stream().map(t -> t.get("id").toString()).collect(Collectors.toSet());
        if (!ratingIds.contains(activeRatingTemplateId)) {
            activeRatingTemplateId = ratingTemplates.get(0).get("id").toString();
        }

        String resolvedActiveTemplateId = activeTemplateId;
        String resolvedActiveRatingTemplateId = activeRatingTemplateId;

        Map<String, Object> activeTemplate = templates.stream()
                .filter(t -> Objects.equals(t.get("id"), resolvedActiveTemplateId))
                .findFirst()
                .orElse(templates.get(0));
        Map<String, Object> activeRatingTemplate = ratingTemplates.stream()
                .filter(t -> Objects.equals(t.get("id"), resolvedActiveRatingTemplateId))
                .findFirst()
                .orElse(ratingTemplates.get(0));

        Map<String, Object> rating = new LinkedHashMap<>();
        rating.put("prompt_text", activeRatingTemplate.get("prompt_text"));
        rating.put("scale_size", activeRatingTemplate.get("scale_size"));
        rating.put("responses", activeRatingTemplate.get("responses"));

        Integer cooldownMinutes = resolveCooldownMinutes(raw, defaults);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("question_templates", templates);
        result.put("active_template_id", resolvedActiveTemplateId);
        result.put("question_flow", castList(activeTemplate.get("question_flow")));
        result.put("rating_templates", ratingTemplates);
        result.put("active_rating_template_id", resolvedActiveRatingTemplateId);
        result.put("rating_system", rating);
        result.put("unblock_request_cooldown_minutes", cooldownMinutes);
        return result;
    }

    public int unblockRequestCooldownMinutes(BotSettingsDto settings, int defaultValue) {
        if (settings == null) {
            return defaultValue;
        }
        Integer value = settings.getUnblockRequestCooldownMinutes();
        if (value == null) {
            return defaultValue;
        }
        return Math.max(0, value);
    }

    private List<Map<String, Object>> sanitizeQuestionFlow(
            Object rawFlow,
            Set<PresetKey> allowedPresets,
            Map<PresetKey, Map<String, Object>> lookup
    ) {
        List<Map<String, Object>> sanitized = new ArrayList<>();
        if (!(rawFlow instanceof Iterable<?>) || rawFlow instanceof String) {
            return sanitized;
        }
        int order = 1;
        for (Object item : (Iterable<?>) rawFlow) {
            Map<String, Object> entry = null;
            if (item instanceof Map<?, ?> map) {
                Object typeValue = map.containsKey("type") ? map.get("type") : map.get("kind");
                String type = optionalString(typeValue);
                if (type.isBlank()) {
                    type = "custom";
                }
                type = type.toLowerCase();
                if (!type.equals("custom") && !type.equals("preset")) {
                    type = "custom";
                }
                Object textValue = map.containsKey("text") ? map.get("text") : map.get("label");
                String text = optionalString(textValue);
                String id = ensureUuid(map.get("id"));
                entry = new LinkedHashMap<>();
                entry.put("id", id);
                entry.put("type", type);
                entry.put("order", order);
                if ("preset".equals(type)) {
                    Map<String, Object> presetPayload = map.get("preset") instanceof Map<?, ?> p ? convertToMap(p) : new LinkedHashMap<>();
                    String group = optionalString(presetPayload.getOrDefault("group", map.get("group")));
                    String field = optionalString(presetPayload.getOrDefault("field", map.get("field")));
                    PresetKey key = new PresetKey(group, field);
                    if (!allowedPresets.contains(key)) {
                        entry = null;
                    } else {
                        entry.put("preset", Map.of("group", group, "field", field));
                        if (text.isBlank()) {
                            Map<String, Object> meta = lookup.getOrDefault(key, Collections.emptyMap());
                            text = optionalString(meta.get("label"));
                            if (text.isBlank()) {
                                text = field;
                            }
                        }
                        List<String> excluded = sanitizeExcludedOptions(map.get("excluded_options"), map.get("exclude"), lookup.getOrDefault(key, Collections.emptyMap()));
                        if (!excluded.isEmpty()) {
                            entry.put("excluded_options", excluded);
                        }
                    }
                } else {
                    if (text.isBlank()) {
                        entry = null;
                    }
                }
                if (entry != null) {
                    entry.put("text", text);
                    sanitized.add(entry);
                    order += 1;
                }
            } else if (item instanceof String str && !str.isBlank()) {
                entry = new LinkedHashMap<>();
                entry.put("id", ensureUuid(null));
                entry.put("type", "custom");
                entry.put("order", order);
                entry.put("text", str.trim());
                sanitized.add(entry);
                order += 1;
            }
        }
        return sanitized;
    }

    private List<String> sanitizeExcludedOptions(Object primary, Object alternative, Map<String, Object> meta) {
        List<String> excluded = new ArrayList<>();
        Collection<?> candidates = null;
        if (primary instanceof Map<?, ?> map) {
            candidates = map.values();
        } else if (primary instanceof Iterable<?> iterable && !(primary instanceof String)) {
            candidates = toCollection(iterable);
        } else if (alternative instanceof Iterable<?> iterable && !(alternative instanceof String)) {
            candidates = toCollection(iterable);
        }
        Set<String> allowedValues = new LinkedHashSet<>();
        Object optionsObj = meta.get("options");
        if (optionsObj instanceof Collection<?> options) {
            for (Object option : options) {
                if (option != null) {
                    allowedValues.add(option.toString());
                }
            }
        }
        if (candidates != null) {
            for (Object candidate : candidates) {
                if (candidate instanceof String str && !str.isBlank()) {
                    String value = str.trim();
                    if (!allowedValues.isEmpty()) {
                        if (allowedValues.contains(value) && !excluded.contains(value)) {
                            excluded.add(value);
                        }
                    } else if (!excluded.contains(value)) {
                        excluded.add(value);
                    }
                }
            }
        }
        return excluded;
    }

    private List<Map<String, Object>> sanitizeRatingResponses(Object rawResponses, int scale, List<Map<String, Object>> defaults) {
        Map<Integer, String> collected = new LinkedHashMap<>();
        Collection<?> iterable = null;
        if (rawResponses instanceof Map<?, ?> map) {
            iterable = map.entrySet();
        } else if (rawResponses instanceof Iterable<?> responses && !(rawResponses instanceof String)) {
            iterable = toCollection(responses);
        }
        if (iterable != null) {
            for (Object item : iterable) {
                Object valueRaw;
                Object textRaw;
                if (item instanceof Map<?, ?> mapItem) {
                    valueRaw = mapItem.get("value");
                    Object textValue = mapItem.containsKey("text") ? mapItem.get("text") : mapItem.get("label");
                    textRaw = textValue;
                } else if (item instanceof List<?> listItem && listItem.size() >= 2) {
                    valueRaw = listItem.get(0);
                    textRaw = listItem.get(1);
                } else if (item instanceof Map.Entry<?, ?> entry) {
                    valueRaw = entry.getKey();
                    textRaw = entry.getValue();
                } else {
                    continue;
                }
                Integer value = tryParseInt(valueRaw);
                if (value == null || value < 1 || value > scale) {
                    continue;
                }
                String text = optionalString(textRaw);
                if (!text.isBlank()) {
                    collected.put(value, text);
                }
            }
        }
        Map<Integer, String> defaultsMap = new LinkedHashMap<>();
        for (Map<String, Object> entry : defaults) {
            Integer key = tryParseInt(entry.get("value"));
            String text = optionalString(entry.get("text"));
            if (key != null && !text.isBlank()) {
                defaultsMap.put(key, text);
            }
        }
        List<Map<String, Object>> responses = new ArrayList<>();
        int upper = Math.max(scale, 1);
        for (int value = 1; value <= upper; value++) {
            String text = collected.getOrDefault(value, defaultsMap.getOrDefault(value, "Спасибо за вашу оценку %d!".formatted(value)));
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("value", value);
            entry.put("text", text);
            responses.add(entry);
        }
        return responses;
    }

    private Map<String, Object> buildLocationPresetsInternal(Map<String, Object> locationTree, Map<String, Object> baseDefinitions) {
        Map<String, Object> result = new LinkedHashMap<>();

        Set<String> businesses = new LinkedHashSet<>();
        Set<String> locationTypes = new LinkedHashSet<>();
        Set<String> cities = new LinkedHashSet<>();
        Set<String> locationNames = new LinkedHashSet<>();

        Map<String, Set<String>> typesByBusiness = new LinkedHashMap<>();
        Map<String, Set<String>> citiesByBusiness = new LinkedHashMap<>();
        Map<String, Set<String>> citiesByType = new LinkedHashMap<>();
        Map<List<String>, Set<String>> citiesByPath = new LinkedHashMap<>();
        Map<String, Set<String>> locationsByBusiness = new LinkedHashMap<>();
        Map<String, Set<String>> locationsByType = new LinkedHashMap<>();
        Map<String, Set<String>> locationsByCity = new LinkedHashMap<>();
        Map<List<String>, Set<String>> locationsByPath = new LinkedHashMap<>();

        if (locationTree != null) {
            for (Map.Entry<String, Object> businessEntry : locationTree.entrySet()) {
                String business = optionalString(businessEntry.getKey());
                if (business.isBlank()) {
                    continue;
                }
                businesses.add(business);
                if (!(businessEntry.getValue() instanceof Map<?, ?> typeMapRaw)) {
                    continue;
                }
                Map<String, Object> typeMap = convertToMap(typeMapRaw);
                for (Map.Entry<String, Object> typeEntry : typeMap.entrySet()) {
                    String locType = optionalString(typeEntry.getKey());
                    if (locType.isBlank()) {
                        continue;
                    }
                    locationTypes.add(locType);
                    typesByBusiness.computeIfAbsent(business, k -> new LinkedHashSet<>()).add(locType);
                    if (!(typeEntry.getValue() instanceof Map<?, ?> cityMapRaw)) {
                        continue;
                    }
                    Map<String, Object> cityMap = convertToMap(cityMapRaw);
                    for (Map.Entry<String, Object> cityEntry : cityMap.entrySet()) {
                        String city = optionalString(cityEntry.getKey());
                        if (city.isBlank()) {
                            continue;
                        }
                        cities.add(city);
                        citiesByBusiness.computeIfAbsent(business, k -> new LinkedHashSet<>()).add(city);
                        citiesByType.computeIfAbsent(locType, k -> new LinkedHashSet<>()).add(city);
                        citiesByPath.computeIfAbsent(List.of(business, locType), k -> new LinkedHashSet<>()).add(city);
                        if (cityEntry.getValue() instanceof Iterable<?> locationsIterable) {
                            for (Object locationObj : locationsIterable) {
                                String locationName = optionalString(locationObj);
                                if (locationName.isBlank()) {
                                    continue;
                                }
                                locationNames.add(locationName);
                                locationsByBusiness.computeIfAbsent(business, k -> new LinkedHashSet<>()).add(locationName);
                                locationsByType.computeIfAbsent(locType, k -> new LinkedHashSet<>()).add(locationName);
                                locationsByCity.computeIfAbsent(city, k -> new LinkedHashSet<>()).add(locationName);
                                locationsByPath.computeIfAbsent(List.of(business, locType, city), k -> new LinkedHashSet<>()).add(locationName);
                            }
                        }
                    }
                }
            }
        }

        Map<String, List<String>> optionMap = new LinkedHashMap<>();
        optionMap.put("business", new ArrayList<>(businesses));
        optionMap.put("location_type", new ArrayList<>(locationTypes));
        optionMap.put("city", new ArrayList<>(cities));
        optionMap.put("location_name", new ArrayList<>(locationNames));
        optionMap.values().forEach(list -> list.sort(Comparator.naturalOrder()));

        Map<String, Map<String, Map<String, Set<Object>>>> optionDependencies = new LinkedHashMap<>();
        optionDependencies.put("location_type", new LinkedHashMap<>());
        optionDependencies.put("city", new LinkedHashMap<>());
        optionDependencies.put("location_name", new LinkedHashMap<>());

        for (Map.Entry<String, Set<String>> entry : typesByBusiness.entrySet()) {
            String business = entry.getKey();
            for (String locType : entry.getValue()) {
                optionDependencies.get("location_type")
                        .computeIfAbsent(locType, k -> new LinkedHashMap<>())
                    .computeIfAbsent("business", k -> new LinkedHashSet<>())
                    .add(business);
            }
        }
        for (Map.Entry<String, Set<String>> entry : citiesByBusiness.entrySet()) {
            String business = entry.getKey();
            for (String city : entry.getValue()) {
                optionDependencies.get("city")
                        .computeIfAbsent(city, k -> new LinkedHashMap<>())
                    .computeIfAbsent("business", k -> new LinkedHashSet<>())
                    .add(business);
            }
        }
        for (Map.Entry<String, Set<String>> entry : citiesByType.entrySet()) {
            String locType = entry.getKey();
            for (String city : entry.getValue()) {
                optionDependencies.get("city")
                        .computeIfAbsent(city, k -> new LinkedHashMap<>())
                    .computeIfAbsent("location_type", k -> new LinkedHashSet<>())
                    .add(locType);
            }
        }
        for (Map.Entry<List<String>, Set<String>> entry : citiesByPath.entrySet()) {
            List<String> path = entry.getKey();
            for (String city : entry.getValue()) {
                optionDependencies.get("city")
                        .computeIfAbsent(city, k -> new LinkedHashMap<>())
                    .computeIfAbsent("paths", k -> new LinkedHashSet<>())
                    .add(List.copyOf(path));
            }
        }
        for (Map.Entry<String, Set<String>> entry : locationsByBusiness.entrySet()) {
            String business = entry.getKey();
            for (String location : entry.getValue()) {
                optionDependencies.get("location_name")
                        .computeIfAbsent(location, k -> new LinkedHashMap<>())
                    .computeIfAbsent("business", k -> new LinkedHashSet<>())
                    .add(business);
            }
        }
        for (Map.Entry<String, Set<String>> entry : locationsByType.entrySet()) {
            String locType = entry.getKey();
            for (String location : entry.getValue()) {
                optionDependencies.get("location_name")
                        .computeIfAbsent(location, k -> new LinkedHashMap<>())
                    .computeIfAbsent("location_type", k -> new LinkedHashSet<>())
                    .add(locType);
            }
        }
        for (Map.Entry<String, Set<String>> entry : locationsByCity.entrySet()) {
            String city = entry.getKey();
            for (String location : entry.getValue()) {
                optionDependencies.get("location_name")
                        .computeIfAbsent(location, k -> new LinkedHashMap<>())
                    .computeIfAbsent("city", k -> new LinkedHashSet<>())
                    .add(city);
            }
        }
        for (Map.Entry<List<String>, Set<String>> entry : locationsByPath.entrySet()) {
            List<String> path = entry.getKey();
            for (String location : entry.getValue()) {
                optionDependencies.get("location_name")
                        .computeIfAbsent(location, k -> new LinkedHashMap<>())
                    .computeIfAbsent("paths", k -> new LinkedHashSet<>())
                    .add(List.copyOf(path));
            }
        }

        Map<String, List<String>> locationTypeTree = new LinkedHashMap<>();
        for (Map.Entry<String, Set<String>> entry : typesByBusiness.entrySet()) {
            List<String> values = new ArrayList<>(entry.getValue());
            values.sort(Comparator.naturalOrder());
            locationTypeTree.put(entry.getKey(), values);
        }
        Map<String, Map<String, List<String>>> cityTree = new LinkedHashMap<>();
        for (Map.Entry<List<String>, Set<String>> entry : citiesByPath.entrySet()) {
            List<String> path = entry.getKey();
            if (path.size() != 2) {
                continue;
            }
            String business = path.get(0);
            String locType = path.get(1);
            List<String> values = new ArrayList<>(entry.getValue());
            values.sort(Comparator.naturalOrder());
            cityTree.computeIfAbsent(business, k -> new LinkedHashMap<>()).put(locType, values);
        }
        Map<String, Map<String, Map<String, List<String>>>> locationTreeMap = new LinkedHashMap<>();
        for (Map.Entry<List<String>, Set<String>> entry : locationsByPath.entrySet()) {
            List<String> path = entry.getKey();
            if (path.size() != 3) {
                continue;
            }
            String business = path.get(0);
            String locType = path.get(1);
            String city = path.get(2);
            List<String> values = new ArrayList<>(entry.getValue());
            values.sort(Comparator.naturalOrder());
            locationTreeMap
                    .computeIfAbsent(business, k -> new LinkedHashMap<>())
                    .computeIfAbsent(locType, k -> new LinkedHashMap<>())
                    .put(city, values);
        }

        Map<String, Map<String, Map<String, Object>>> finalizedDependencies = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, Map<String, Set<Object>>>> entry : optionDependencies.entrySet()) {
            Map<String, Map<String, Object>> finalized = new LinkedHashMap<>();
            for (Map.Entry<String, Map<String, Set<Object>>> optionEntry : entry.getValue().entrySet()) {
                Map<String, Object> optionMapEntry = new LinkedHashMap<>();
                for (Map.Entry<String, Set<Object>> depEntry : optionEntry.getValue().entrySet()) {
                    if ("paths".equals(depEntry.getKey())) {
                        Set<?> paths = depEntry.getValue();
                        if (paths.isEmpty()) {
                            continue;
                        }
                        if (paths.stream().allMatch(p -> p instanceof List<?> list && list.size() == 2)) {
                            List<Map<String, String>> prepared = paths.stream()
                                    .map(p -> (List<?>) p)
                                    .map(list -> Map.of("business", list.get(0).toString(), "location_type", list.get(1).toString()))
                                    .sorted(Comparator.comparing(m -> m.get("business") + "|" + m.get("location_type")))
                                    .collect(Collectors.toList());
                            optionMapEntry.put("paths", prepared);
                        } else if (paths.stream().allMatch(p -> p instanceof List<?> list && list.size() == 3)) {
                            List<Map<String, String>> prepared = paths.stream()
                                    .map(p -> (List<?>) p)
                                    .map(list -> Map.of(
                                            "business", list.get(0).toString(),
                                            "location_type", list.get(1).toString(),
                                            "city", list.get(2).toString()))
                                    .sorted(Comparator.comparing(m -> m.get("business") + "|" + m.get("location_type") + "|" + m.get("city")))
                                    .collect(Collectors.toList());
                            optionMapEntry.put("paths", prepared);
                        }
                        continue;
                    }
                    List<String> values = depEntry.getValue().stream()
                            .map(Object::toString)
                            .sorted()
                            .collect(Collectors.toList());
                    if (!values.isEmpty()) {
                        optionMapEntry.put(depEntry.getKey(), values);
                    }
                }
                if (!optionMapEntry.isEmpty()) {
                    finalized.put(optionEntry.getKey(), optionMapEntry);
                }
            }
            finalizedDependencies.put(entry.getKey(), finalized);
        }

        Set<String> locationFields = Set.of("business", "location_type", "city", "location_name");
        for (Map.Entry<String, Object> groupEntry : baseDefinitions.entrySet()) {
            Map<String, Object> groupData = groupEntry.getValue() instanceof Map<?, ?> ? convertToMap(groupEntry.getValue()) : new LinkedHashMap<>();
            String groupLabel = optionalString(groupData.get("label"));
            Map<String, Object> fields = groupData.get("fields") instanceof Map<?, ?> ? convertToMap(groupData.get("fields")) : new LinkedHashMap<>();
            Map<String, Object> preparedFields = new LinkedHashMap<>();
            for (Map.Entry<String, Object> fieldEntry : fields.entrySet()) {
                String fieldKey = fieldEntry.getKey();
                Map<String, Object> fieldMeta = fieldEntry.getValue() instanceof Map<?, ?> ? convertToMap(fieldEntry.getValue()) : new LinkedHashMap<>();
                String fieldLabel = optionalString(fieldMeta.get("label"));
                boolean isLocationField = locationFields.contains(fieldKey);
                List<String> fieldOptions = new ArrayList<>();
                Object fieldOptionsRaw = fieldMeta.get("options");
                if (!isLocationField && fieldOptionsRaw instanceof Iterable<?> iterable) {
                    for (Object option : toCollection(iterable)) {
                        String value = optionalString(option);
                        if (!value.isBlank()) {
                            fieldOptions.add(value);
                        }
                    }
                }
                if (isLocationField) {
                    fieldOptions = new ArrayList<>(optionMap.getOrDefault(fieldKey, List.of()));
                }
                Map<String, Object> fieldResult = new LinkedHashMap<>();
                fieldResult.put("label", fieldLabel.isBlank() ? fieldKey : fieldLabel);
                fieldResult.put("options", fieldOptions);
                Map<String, Object> deps = null;
                if (isLocationField) {
                    Map<String, Map<String, Object>> locationDeps = finalizedDependencies.get(fieldKey);
                    if (locationDeps != null && !locationDeps.isEmpty()) {
                        deps = new LinkedHashMap<>(locationDeps);
                    }
                }
                if (deps == null || deps.isEmpty()) {
                    Object rawDeps = fieldMeta.get("option_dependencies");
                    if (rawDeps instanceof Map<?, ?> rawDepsMap && !rawDepsMap.isEmpty()) {
                        deps = new LinkedHashMap<>();
                        for (Map.Entry<?, ?> entry : rawDepsMap.entrySet()) {
                            if (entry.getKey() != null) {
                                deps.put(entry.getKey().toString(), entry.getValue());
                            }
                        }
                    }
                }
                if (deps != null && !deps.isEmpty()) {
                    fieldResult.put("option_dependencies", deps);
                }
                if (isLocationField && "location_type".equals(fieldKey) && !locationTypeTree.isEmpty()) {
                    fieldResult.put("tree", locationTypeTree);
                } else if (isLocationField && "city".equals(fieldKey) && !cityTree.isEmpty()) {
                    fieldResult.put("tree", cityTree);
                } else if (isLocationField && "location_name".equals(fieldKey) && !locationTreeMap.isEmpty()) {
                    fieldResult.put("tree", locationTreeMap);
                }
                preparedFields.put(fieldKey, fieldResult);
            }
            Map<String, Object> preparedGroup = new LinkedHashMap<>();
            preparedGroup.put("label", groupLabel.isBlank() ? groupEntry.getKey() : groupLabel);
            preparedGroup.put("fields", preparedFields);
            result.put(groupEntry.getKey(), preparedGroup);
        }
        return result;
    }

    private List<Map<String, Object>> buildDefaultResponses(int scale) {
        List<Map<String, Object>> responses = new ArrayList<>();
        int upperBound = Math.max(scale, 1);
        for (int value = 1; value <= upperBound; value++) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("value", value);
            response.put("text", "Спасибо за вашу оценку %d! Нам важно ваше мнение.".formatted(value));
            responses.add(response);
        }
        return responses;
    }

    private String defaultRatingPrompt(int scale) {
        if (scale <= 1) {
            return "Пожалуйста, оцените качество ответа: отправьте число 1.";
        }
        return "Пожалуйста, оцените качество ответа от 1 до %d.".formatted(scale);
    }

    private String ensureUuid(Object value) {
        if (value instanceof String str && !str.isBlank()) {
            return str.trim();
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String capitalize(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        String cleaned = value.trim().replace('_', ' ');
        return cleaned.substring(0, 1).toUpperCase() + cleaned.substring(1);
    }

    private String optionalString(Object value) {
        if (value instanceof String str) {
            return str.trim();
        }
        return value == null ? "" : value.toString().trim();
    }

    private Integer tryParseInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String str && !str.isBlank()) {
            try {
                return Integer.parseInt(str.trim());
            } catch (NumberFormatException ignore) {
                return null;
            }
        }
        return null;
    }

    private Integer resolveCooldownMinutes(Map<String, Object> raw, Map<String, Object> defaults) {
        Integer fallback = tryParseInt(defaults.get("unblock_request_cooldown_minutes"));
        Integer value = tryParseInt(raw.get("unblock_request_cooldown_minutes"));
        if (value == null) {
            value = tryParseInt(raw.get("unblockRequestCooldownMinutes"));
        }
        if (value == null) {
            value = fallback != null ? fallback : 60;
        }
        return Math.max(0, value);
    }

    private int normalizeScale(Object rawScale, Object altScale, Object camelScale, Map<String, Object> defaults, int maxScale) {
        Integer  fallback = tryParseInt(((Map<String, Object>) defaults.get("rating_system")).get("scale_size"));
        Integer value = tryParseInt(rawScale);
        if (value == null) {
            value = tryParseInt(altScale);
        }
        if (value == null) {
            value = tryParseInt(camelScale);
        }
        if (value == null) {
            value = fallback != null ? fallback : 5;
        }
        if (value < 1) {
            value = 1;
        }
        if (maxScale > 0 && value > maxScale) {
            value = maxScale;
        }
        return value;
    }

    private List<Map<String, Object>> castList(Object value) {
        if (value instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return new ArrayList<>();
    }

    private Object defaultsValue(Map<String, Object> defaults, String listKey, int index, String mapKey) {
        Object listObj = defaults.get(listKey);
        if (listObj instanceof List<?> list && list.size() > index) {
            Object map = list.get(index);
            if (map instanceof Map<?, ?> mapEntry) {
                return mapEntry.get(mapKey);
            }
        }
        return null;
    }

    private Collection<?> toCollection(Iterable<?> iterable) {
        if (iterable instanceof Collection<?> collection) {
            return collection;
        }
        List<Object> list = new ArrayList<>();
        iterable.forEach(list::add);
        return list;
    }

    private record PresetKey(String group, String field) {
    }
}
