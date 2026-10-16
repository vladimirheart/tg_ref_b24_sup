package com.example.panel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/** Links one business to one distinct, unchanged template in canonical bot_settings. */
@Service
public class BusinessQuestionTemplateRegistryService {

    private final JdbcTemplate jdbc;
    private final SharedConfigService sharedConfigService;
    private final BotSettingsPayloadNormalizer normalizer;
    private final ObjectMapper objectMapper;

    public BusinessQuestionTemplateRegistryService(JdbcTemplate jdbc, SharedConfigService sharedConfigService,
            BotSettingsPayloadNormalizer normalizer, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.sharedConfigService = sharedConfigService;
        this.normalizer = normalizer;
        this.objectMapper = objectMapper;
    }

    public List<TemplateSummary> availableTemplates() {
        return loadTemplates().stream()
                .filter(template -> !isLegacyAssigned(template.id()))
                .map(template -> new TemplateSummary(template.id(), template.name()))
                .toList();
    }

    public Optional<Binding> findBinding(long businessId) {
        requirePositive(businessId);
        return jdbc.query("""
                SELECT business_id, template_id, template_sha256, is_active
                  FROM business_question_template_bindings WHERE business_id = ?
                """, (rs, row) -> new Binding(rs.getLong("business_id"), rs.getString("template_id"),
                rs.getString("template_sha256"), rs.getBoolean("is_active")), businessId).stream().findFirst();
    }

    /** Bind a distinct existing ID; cloning in the settings editor precedes this call. */
    public Optional<Binding> bind(long businessId, String templateId) throws DuplicateKeyException {
        requirePositive(businessId);
        if (templateId == null || templateId.isBlank() || !templateId.equals(templateId.strip())) {
            throw new IllegalArgumentException("Question template ID is required");
        }
        Template template = loadTemplates().stream()
                .filter(item -> templateId.equals(item.id()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Question template not found or unsuitable"));
        if (isLegacyAssigned(templateId)) {
            throw new IllegalArgumentException("Clone the legacy question template before binding it to a business");
        }
        if (jdbc.queryForList("SELECT id FROM businesses WHERE id = ? AND status = 'active'", Long.class,
                businessId).isEmpty()) {
            return Optional.empty();
        }
        String fingerprint = fingerprint(template.payload());
        jdbc.update("""
                INSERT INTO business_question_template_bindings(business_id, template_id, template_sha256, is_active)
                VALUES (?, ?, ?, TRUE)
                ON CONFLICT (business_id) DO UPDATE SET
                    template_id = EXCLUDED.template_id,
                    template_sha256 = EXCLUDED.template_sha256,
                    is_active = TRUE,
                    updated_at = CURRENT_TIMESTAMP
                """, businessId, templateId, fingerprint);
        return Optional.of(new Binding(businessId, templateId, fingerprint, true));
    }

    public boolean deactivate(long businessId) {
        requirePositive(businessId);
        return jdbc.update("""
                UPDATE business_question_template_bindings
                   SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP
                 WHERE business_id = ? AND is_active = TRUE
                """, businessId) == 1;
    }

    /** Mixed/archived/unbound channels fail closed; a stale B template cannot change A's flow. */
    public Optional<ScopedRuntimeConfig> resolveExplicitChannel(long channelId, long businessId) {
        requirePositive(channelId);
        requirePositive(businessId);
        List<ChannelBinding> bindings = jdbc.query("""
                SELECT binding.business_id, binding.resolution_mode, business.status,
                       template.template_id, template.template_sha256, template.is_active AS template_active
                  FROM channels channel
                  JOIN business_channel_bindings binding ON binding.channel_id = channel.id
                  JOIN businesses business ON business.id = binding.business_id
                  LEFT JOIN business_question_template_bindings template
                    ON template.business_id = binding.business_id
                 WHERE channel.id = ? AND channel.is_active = TRUE AND binding.is_active = TRUE
                """, (rs, row) -> new ChannelBinding(rs.getLong("business_id"),
                rs.getString("resolution_mode"), rs.getString("status"),
                rs.getString("template_id"), rs.getString("template_sha256"),
                rs.getBoolean("template_active")), channelId);
        if (bindings.isEmpty() || bindings.stream().anyMatch(binding ->
                !"EXPLICIT_SELECTION".equals(binding.resolutionMode())
                    || !"active".equals(binding.businessStatus())
                    || binding.templateId() == null || binding.fingerprint() == null
                    || !binding.templateActive())) {
            return Optional.empty();
        }
        Map<String, Template> templates = new LinkedHashMap<>();
        for (Template template : loadTemplates()) {
            templates.putIfAbsent(template.id(), template);
        }
        Optional<ChannelBinding> selected = bindings.stream()
                .filter(binding -> binding.businessId() == businessId).findFirst();
        if (selected.isEmpty()) {
            return Optional.empty();
        }
        ChannelBinding binding = selected.get();
        Template template = templates.get(binding.templateId());
        if (template == null || isLegacyAssigned(binding.templateId())
                || !binding.fingerprint().equals(fingerprint(template.payload()))) {
            return Optional.empty();
        }
        Map<String, Object> botSettings = new LinkedHashMap<>();
        botSettings.put("question_templates", List.of(template.payload()));
        botSettings.put("active_template_id", template.id());
        return Optional.of(new ScopedRuntimeConfig(channelId, businessId, template.id(),
                binding.fingerprint(), botSettings, Map.of(), Map.of()));
    }

    private List<Template> loadTemplates() {
        Object raw = normalizer.normalize(sharedConfigService.loadSettings().get("bot_settings"))
                .get("question_templates");
        if (!(raw instanceof List<?> source)) {
            return List.of();
        }
        List<Template> templates = new ArrayList<>();
        Set<String> seenIds = new HashSet<>();
        Set<String> duplicateIds = new HashSet<>();
        for (Object item : source) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            map.forEach((key, value) -> {
                if (key != null) {
                    payload.put(key.toString(), value);
                }
            });
            String id = text(payload.get("id"));
            Object flow = payload.get("question_flow");
            if (id.isEmpty() || !(flow instanceof List<?> questions) || questions.isEmpty()
                    || questions.stream().anyMatch(question -> !(question instanceof Map<?, ?> questionMap)
                        || text(questionMap.get("id")).isEmpty())
                    || hasUnsupportedPresets(questions)) {
                continue;
            }
            if (!seenIds.add(id)) {
                duplicateIds.add(id);
            }
            templates.add(new Template(id, text(payload.get("name")), payload));
        }
        templates.removeIf(template -> duplicateIds.contains(template.id()));
        return templates;
    }

    private boolean isLegacyAssigned(String templateId) {
        Map<String, Object> botSettings = normalizer.normalize(
                sharedConfigService.loadSettings().get("bot_settings"));
        if (templateId.equals(text(botSettings.get("active_template_id")))) {
            return true;
        }
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM channels WHERE question_template_id = ?)
                """, Boolean.class, templateId));
    }

    // Until canonical business_locations are wired to bot presets, no global location tree is exposed.
    private boolean hasUnsupportedPresets(List<?> questions) {
        return questions.stream().anyMatch(question -> question instanceof Map<?, ?> map
                && ("preset".equalsIgnoreCase(text(map.get("type"))) || map.get("preset") != null));
    }

    private String fingerprint(Map<String, Object> payload) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(objectMapper.writeValueAsBytes(payload)));
        } catch (NoSuchAlgorithmException | JsonProcessingException ex) {
            throw new IllegalStateException("Cannot fingerprint question template", ex);
        }
    }

    private String text(Object value) {
        return value == null ? "" : value.toString().strip();
    }

    private void requirePositive(long id) {
        if (id <= 0) {
            throw new IllegalArgumentException("Invalid identifier");
        }
    }

    private record Template(String id, String name, Map<String, Object> payload) {
    }

    private record ChannelBinding(long businessId, String resolutionMode, String businessStatus,
                                  String templateId, String fingerprint, boolean templateActive) {
    }

    public record TemplateSummary(String id, String name) {
    }

    public record Binding(long businessId, String templateId, String templateSha256, boolean active) {
    }

    public record ScopedRuntimeConfig(long channelId, long businessId, String questionTemplateId,
                                      String templateSha256, Map<String, Object> botSettings,
                                      Map<String, Object> locationTree,
                                      Map<String, Object> presetDefinitions) {
    }
}
