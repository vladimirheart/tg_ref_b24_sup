package com.example.panel.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Version 2 catalog and immutable selection snapshot; the ingestion writer independently verifies it. */
@Service
public class SharedGeneralQuestionnaireService {
    private final JdbcTemplate jdbc;
    private final BusinessQuestionTemplateRegistryService businesses;
    private final SharedConfigService settings;
    private final BotSettingsPayloadNormalizer normalizer;
    private final ObjectMapper mapper;

    public SharedGeneralQuestionnaireService(JdbcTemplate jdbc, BusinessQuestionTemplateRegistryService businesses,
            SharedConfigService settings, BotSettingsPayloadNormalizer normalizer, ObjectMapper mapper) {
        this.jdbc = jdbc; this.businesses = businesses; this.settings = settings;
        this.normalizer = normalizer; this.mapper = mapper;
    }

    public Optional<Binding> findBinding(long channelId) {
        positive(channelId);
        return jdbc.query("""
            SELECT channel_id, template_id, template_sha256, is_active
            FROM shared_channel_general_question_template_bindings WHERE channel_id=?
            """, (rs, row) -> new Binding(rs.getLong("channel_id"), rs.getString("template_id"),
                rs.getString("template_sha256"), rs.getBoolean("is_active")), channelId).stream().findFirst();
    }

    @Transactional
    public Optional<Binding> bind(long channelId, String templateId) {
        positive(channelId);
        if (templateId == null || templateId.isBlank() || !templateId.equals(templateId.strip()))
            throw new IllegalArgumentException("Question template ID is required");
        // All three binding writers use the same transaction-scoped template lock.
        jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtext('question-template-scope'),hashtext(?))", templateId);
        var channels = jdbc.queryForList("SELECT id FROM channels WHERE id=? AND is_active=TRUE FOR UPDATE", Long.class, channelId);
        if (channels.isEmpty()) return Optional.empty();
        var catalog = businesses.choices(channelId);
        if (catalog.isEmpty() || catalog.get().choices().size() >= 100)
            throw new IllegalStateException("A valid explicit-selection business catalog is required");
        if (templateReservedElsewhere(templateId))
            throw new IllegalStateException("Clone a distinct non-business, non-legacy question template first");
        var template = template(templateId).orElseThrow(() -> new IllegalArgumentException("Question template not found or unsuitable"));
        String sha = fingerprint(template);
        jdbc.update("""
            INSERT INTO shared_channel_general_question_template_bindings(channel_id,template_id,template_sha256,is_active)
            VALUES (?,?,?,TRUE) ON CONFLICT(channel_id) DO UPDATE SET template_id=EXCLUDED.template_id,
                template_sha256=EXCLUDED.template_sha256,is_active=TRUE,updated_at=CURRENT_TIMESTAMP
            """, channelId, templateId, sha);
        return Optional.of(new Binding(channelId, templateId, sha, true));
    }

    @Transactional
    public boolean deactivate(long channelId) {
        positive(channelId);
        return jdbc.update("""
            UPDATE shared_channel_general_question_template_bindings SET is_active=FALSE,updated_at=CURRENT_TIMESTAMP
            WHERE channel_id=? AND is_active=TRUE
            """, channelId) == 1;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<Catalog> choices(long channelId) {
        return snapshot(channelId).map(Snapshot::catalog);
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public Optional<Selected> select(long channelId, String scopeKind, Long businessId, String catalogSha256) {
        positive(channelId);
        if (!("GENERAL".equals(scopeKind) && businessId == null)
                && !("BUSINESS".equals(scopeKind) && businessId != null && businessId > 0))
            throw new IllegalArgumentException("Invalid questionnaire choice identity");
        if (catalogSha256 == null || !catalogSha256.matches("[a-f0-9]{64}"))
            throw new IllegalArgumentException("Invalid catalog fingerprint");
        return snapshot(channelId).filter(s -> s.catalog().catalogSha256().equals(catalogSha256)).flatMap(s -> {
            var choice = s.catalog().choices().stream().filter(c -> scopeKind.equals(c.scopeKind())
                && Objects.equals(businessId, c.businessId())).findFirst();
            if (choice.isEmpty()) return Optional.empty();
            var c = choice.get();
            if ("GENERAL".equals(scopeKind)) return Optional.of(new Selected(2, channelId, "GENERAL", "USER_SELECTION",
                null, c.questionTemplateId(), c.templateSha256(), catalogSha256,
                Map.of("active_template_id", c.questionTemplateId(), "question_templates", List.of(s.generalTemplate())),
                Map.of(), Map.of()));
            return businesses.select(channelId, businessId, s.businessCatalog().catalogSha256()).map(b ->
                new Selected(2, channelId, "BUSINESS", "USER_SELECTION", b.businessId(), b.questionTemplateId(),
                    b.templateSha256(), catalogSha256, b.botSettings(), Map.of(), Map.of()));
        });
    }

    private Optional<Snapshot> snapshot(long channelId) {
        positive(channelId);
        var businessCatalog = businesses.choices(channelId);
        if (businessCatalog.isEmpty()) return Optional.empty();
        List<Choice> choices = new ArrayList<>();
        for (var choice : businessCatalog.get().choices()) {
            choices.add(new Choice("BUSINESS", choice.businessId(), choice.code(), choice.displayName(),
                choice.questionTemplateId(), choice.templateSha256()));
        }
        Map<String, Object> generalTemplate = Map.of();
        var binding = findBinding(channelId).filter(Binding::active);
        if (binding.isPresent()) {
            var general = binding.get();
            var template = template(general.templateId());
            if (templateReservedElsewhere(general.templateId()) || template.isEmpty()
                    || !general.templateSha256().equals(fingerprint(template.get())) || choices.size() >= 100)
                return Optional.empty();
            choices.add(new Choice("GENERAL", null, "GENERAL", "Без бизнеса", general.templateId(), general.templateSha256()));
            generalTemplate = template.get();
        }
        var identity = new LinkedHashMap<String, Object>();
        identity.put("version", 2); identity.put("channelId", channelId); identity.put("choices", choices);
        String digest = fingerprint(identity);
        return Optional.of(new Snapshot(new Catalog(2, channelId, digest, List.copyOf(choices)),
            businessCatalog.get(), generalTemplate));
    }

    private boolean templateReservedElsewhere(String templateId) {
        var bot = normalizer.normalize(settings.loadSettings().get("bot_settings"));
        if (templateId.equals(text(bot.get("active_template_id")))) return true;
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM business_question_template_bindings WHERE template_id=?)
                OR EXISTS(SELECT 1 FROM general_channel_question_template_bindings WHERE template_id=?)
                OR EXISTS(SELECT 1 FROM channels WHERE question_template_id=?)
            """, Boolean.class, templateId, templateId, templateId));
    }

    private Optional<Map<String, Object>> template(String templateId) {
        Object source = normalizer.normalize(settings.loadSettings().get("bot_settings")).get("question_templates");
        if (!(source instanceof List<?> templates)) return Optional.empty();
        List<Map<String, Object>> matches = new ArrayList<>();
        for (Object item : templates) {
            if (item instanceof Map<?, ?> map && templateId.equals(text(map.get("id"))))
                matches.add(mapper.convertValue(map, new TypeReference<Map<String, Object>>() {}));
        }
        if (matches.size() != 1 || !(matches.get(0).get("question_flow") instanceof List<?> flow) || flow.isEmpty())
            return Optional.empty();
        Set<String> ids = new HashSet<>();
        for (Object item : flow) {
            if (!(item instanceof Map<?, ?> q) || text(q.get("id")).isEmpty() || !ids.add(text(q.get("id")))
                    || "preset".equalsIgnoreCase(text(q.get("type"))) || q.get("preset") != null)
                return Optional.empty();
        }
        return Optional.of(matches.get(0));
    }

    private String fingerprint(Map<String, Object> value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(mapper.writeValueAsBytes(value)));
        } catch (NoSuchAlgorithmException | JsonProcessingException ex) {
            throw new IllegalStateException("Cannot fingerprint questionnaire", ex);
        }
    }
    private String text(Object value) { return value == null ? "" : value.toString().strip(); }
    private void positive(long id) { if (id <= 0) throw new IllegalArgumentException("Invalid channel ID"); }

    public record Binding(long channelId, String templateId, String templateSha256, boolean active) {}
    public record Choice(String scopeKind, Long businessId, String code, String displayName,
                         String questionTemplateId, String templateSha256) {}
    public record Catalog(int version, long channelId, String catalogSha256, List<Choice> choices) {}
    public record Selected(int version, long channelId, String scopeKind, String scopeSelectionSource, Long businessId,
                           String questionTemplateId, String templateSha256, String catalogSha256,
                           Map<String, Object> botSettings, Map<String, Object> locationTree, Map<String, Object> presetDefinitions) {}
    private record Snapshot(Catalog catalog, BusinessQuestionTemplateRegistryService.ChoiceCatalog businessCatalog,
                            Map<String, Object> generalTemplate) {}
}
