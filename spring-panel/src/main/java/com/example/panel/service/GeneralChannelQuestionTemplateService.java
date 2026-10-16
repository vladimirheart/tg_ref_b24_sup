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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit opt-in for a business-free channel; no inference from missing bindings. */
@Service
public class GeneralChannelQuestionTemplateService {

    private final JdbcTemplate jdbc;
    private final SharedConfigService sharedConfigService;
    private final BotSettingsPayloadNormalizer normalizer;
    private final ObjectMapper objectMapper;

    public GeneralChannelQuestionTemplateService(JdbcTemplate jdbc, SharedConfigService sharedConfigService,
            BotSettingsPayloadNormalizer normalizer, ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.sharedConfigService = sharedConfigService;
        this.normalizer = normalizer;
        this.objectMapper = objectMapper;
    }

    public Optional<Binding> findBinding(long channelId) {
        requirePositive(channelId);
        return jdbc.query("""
                SELECT channel_id, template_id, template_sha256, is_active
                  FROM general_channel_question_template_bindings WHERE channel_id = ?
                """, (rs, row) -> new Binding(rs.getLong("channel_id"), rs.getString("template_id"),
                rs.getString("template_sha256"), rs.getBoolean("is_active")), channelId).stream().findFirst();
    }

    @Transactional
    public Optional<Binding> bind(long channelId, String templateId) {
        requirePositive(channelId);
        if (templateId == null || templateId.isBlank() || !templateId.equals(templateId.strip())) {
            throw new IllegalArgumentException("Question template ID is required");
        }
        List<ChannelSelection> channels = jdbc.query("""
                SELECT question_template_id, is_active FROM channels WHERE id = ? FOR UPDATE
                """, (rs, row) -> new ChannelSelection(rs.getString("question_template_id"),
                rs.getBoolean("is_active")), channelId);
        if (channels.isEmpty() || !channels.get(0).active()) {
            return Optional.empty();
        }
        if (!templateId.equals(channels.get(0).templateId())) {
            throw new IllegalArgumentException("Select this question template on the channel first");
        }
        if (hasActiveBusinessBindings(channelId)) {
            throw new IllegalStateException("General channel cannot have active business bindings");
        }
        if (isBusinessTemplate(templateId)) {
            throw new IllegalStateException("Question template is bound to a business");
        }
        Template template = findTemplate(templateId)
                .orElseThrow(() -> new IllegalArgumentException("Question template not found or unsuitable"));
        String sha256 = fingerprint(template.payload());
        jdbc.update("""
                INSERT INTO general_channel_question_template_bindings
                    (channel_id, template_id, template_sha256, is_active)
                VALUES (?, ?, ?, TRUE)
                ON CONFLICT (channel_id) DO UPDATE SET
                    template_id = EXCLUDED.template_id,
                    template_sha256 = EXCLUDED.template_sha256,
                    is_active = TRUE,
                    updated_at = CURRENT_TIMESTAMP
                """, channelId, templateId, sha256);
        return Optional.of(new Binding(channelId, templateId, sha256, true));
    }

    @Transactional
    public boolean deactivate(long channelId) {
        requirePositive(channelId);
        return jdbc.update("""
                UPDATE general_channel_question_template_bindings
                   SET is_active = FALSE, updated_at = CURRENT_TIMESTAMP
                 WHERE channel_id = ? AND is_active = TRUE
                """, channelId) == 1;
    }

    /** Rechecks channel, business isolation and unchanged template before any runtime use/write. */
    public Optional<RuntimeConfig> resolve(long channelId) {
        requirePositive(channelId);
        List<Binding> bindings = jdbc.query("""
                SELECT binding.channel_id, binding.template_id, binding.template_sha256, binding.is_active
                  FROM general_channel_question_template_bindings binding
                  JOIN channels channel ON channel.id = binding.channel_id
                 WHERE binding.channel_id = ? AND binding.is_active = TRUE
                   AND channel.is_active = TRUE
                   AND channel.question_template_id = binding.template_id
                   AND NOT EXISTS (
                       SELECT 1 FROM business_channel_bindings business_binding
                        WHERE business_binding.channel_id = channel.id
                          AND business_binding.is_active = TRUE)
                   AND NOT EXISTS (
                       SELECT 1 FROM business_question_template_bindings business_template
                        WHERE business_template.template_id = binding.template_id)
                """, (rs, row) -> new Binding(rs.getLong("channel_id"), rs.getString("template_id"),
                rs.getString("template_sha256"), rs.getBoolean("is_active")), channelId);
        if (bindings.size() != 1) {
            return Optional.empty();
        }
        Binding binding = bindings.get(0);
        Optional<Template> template = findTemplate(binding.templateId());
        if (template.isEmpty() || !binding.templateSha256().equals(fingerprint(template.get().payload()))) {
            return Optional.empty();
        }
        Map<String, Object> botSettings = new LinkedHashMap<>(template.get().botSettings());
        botSettings.put("question_templates", List.of(template.get().payload()));
        botSettings.put("active_template_id", binding.templateId());
        return Optional.of(new RuntimeConfig(channelId, "GENERAL", "GENERAL_CHANNEL",
                binding.templateId(), binding.templateSha256(), botSettings, Map.of(), Map.of()));
    }

    private Optional<Template> findTemplate(String templateId) {
        Map<String, Object> botSettings = normalizer.normalize(sharedConfigService.loadSettings().get("bot_settings"));
        Object raw = botSettings.get("question_templates");
        if (!(raw instanceof List<?> source)) {
            return Optional.empty();
        }
        List<Map<String, Object>> matches = new ArrayList<>();
        for (Object item : source) {
            if (!(item instanceof Map<?, ?> sourceMap)) {
                continue;
            }
            Map<String, Object> payload = new LinkedHashMap<>();
            sourceMap.forEach((key, value) -> {
                if (key != null) {
                    payload.put(key.toString(), value);
                }
            });
            if (templateId.equals(text(payload.get("id")))) {
                matches.add(payload);
            }
        }
        if (matches.size() != 1 || !(matches.get(0).get("question_flow") instanceof List<?> questions)
                || questions.isEmpty() || !validQuestions(questions)) {
            return Optional.empty();
        }
        return Optional.of(new Template(matches.get(0), botSettings));
    }

    private boolean validQuestions(List<?> questions) {
        Set<String> ids = new HashSet<>();
        for (Object item : questions) {
            if (!(item instanceof Map<?, ?> question) || text(question.get("id")).isEmpty()
                    || !ids.add(text(question.get("id")))
                    || "preset".equalsIgnoreCase(text(question.get("type")))
                    || question.get("preset") != null) {
                return false;
            }
        }
        return true;
    }

    private boolean hasActiveBusinessBindings(long channelId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM business_channel_bindings
                                WHERE channel_id = ? AND is_active = TRUE)
                """, Boolean.class, channelId));
    }

    private boolean isBusinessTemplate(String templateId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM business_question_template_bindings
                                WHERE template_id = ?)
                """, Boolean.class, templateId));
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
            throw new IllegalArgumentException("Invalid channel identifier");
        }
    }

    private record ChannelSelection(String templateId, boolean active) {
    }

    private record Template(Map<String, Object> payload, Map<String, Object> botSettings) {
    }

    public record Binding(long channelId, String templateId, String templateSha256, boolean active) {
    }

    public record RuntimeConfig(long channelId, String scopeKind, String scopeSelectionSource,
                                String questionTemplateId, String templateSha256,
                                Map<String, Object> botSettings, Map<String, Object> locationTree,
                                Map<String, Object> presetDefinitions) {
    }
}
