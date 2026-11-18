package com.example.panel.service;

import com.example.panel.service.integration.ConversationTicketCreatedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Resolves the opt-in single questionnaire assigned to a channel.
 *
 * <p>The template stays reusable: a {@code business} question is expanded at runtime from
 * the channel's active business bindings. The produced option id is the canonical database
 * id, never the business display name.</p>
 */
@Service
public class UnifiedChannelQuestionnaireService {

    public static final String SCOPE_MODE = "UNIFIED_FIELDS";
    public static final String SCOPE_KIND = "QUESTIONNAIRE";
    public static final String SCOPE_SOURCE = "QUESTIONNAIRE_FIELD";

    private final JdbcTemplate jdbc;
    private final SharedConfigService settings;
    private final BotSettingsPayloadNormalizer normalizer;
    private final ObjectMapper mapper;

    public UnifiedChannelQuestionnaireService(JdbcTemplate jdbc,
                                              SharedConfigService settings,
                                              BotSettingsPayloadNormalizer normalizer,
                                              ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.normalizer = normalizer;
        this.mapper = mapper;
    }

    /** Returns no value for ordinary and legacy question templates. */
    public Optional<RuntimeConfig> resolve(long channelId) {
        if (channelId <= 0) {
            return Optional.empty();
        }
        List<ChannelSelection> channels = jdbc.query("""
                SELECT id, question_template_id
                  FROM channels
                 WHERE id = ? AND is_active = TRUE
                """, (rs, row) -> new ChannelSelection(rs.getLong("id"), rs.getString("question_template_id")), channelId);
        if (channels.size() != 1 || blank(channels.get(0).templateId())) {
            return Optional.empty();
        }

        Map<String, Object> botSettings = normalizer.normalize(settings.loadSettings().get("bot_settings"));
        Map<String, Object> template = findTemplate(botSettings, channels.get(0).templateId()).orElse(null);
        if (template == null || !SCOPE_MODE.equals(text(template.get("scope_mode")))) {
            return Optional.empty();
        }

        List<Map<String, Object>> rawFlow;
        try {
            rawFlow = QuestionnaireFlowContract.questions(template);
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }
        if (rawFlow.stream().anyMatch(question -> blank(text(question.get("text")))
                || ("business".equals(QuestionnaireFlowContract.key(question))
                    && !"business".equals(QuestionnaireFlowContract.type(question))))) {
            return Optional.empty();
        }

        List<BusinessOption> businesses = activeBusinesses(channelId);
        Map<String, Object> expanded = copy(template);
        List<Map<String, Object>> expandedFlow = new ArrayList<>();
        for (Map<String, Object> question : rawFlow) {
            Map<String, Object> effective = copy(question);
            if ("business".equals(QuestionnaireFlowContract.type(question))) {
                if (businesses.isEmpty()) {
                    return Optional.empty();
                }
                effective.put("type", "select");
                effective.put("options", businesses.stream()
                        .map(option -> Map.<String, Object>of("id", Long.toString(option.id()), "label", option.displayName()))
                        .toList());
            }
            expandedFlow.add(effective);
        }
        expanded.put("question_flow", expandedFlow);
        try {
            QuestionnaireFlowContract.questions(expanded);
        } catch (IllegalArgumentException invalid) {
            return Optional.empty();
        }

        Map<String, Object> effectiveSettings = copy(botSettings);
        effectiveSettings.put("question_templates", List.of(expanded));
        effectiveSettings.put("active_template_id", channels.get(0).templateId());
        return Optional.of(new RuntimeConfig(channelId, channels.get(0).templateId(), fingerprint(mapper, expanded),
                effectiveSettings, expanded, businesses));
    }

    /** Verifies both the frozen template version and the canonical business answer. */
    public void requireMatching(ConversationTicketCreatedEvent event) {
        if (event == null || event.channelId() == null || event.channelId() <= 0
                || !SCOPE_SOURCE.equals(event.scopeSelectionSource())
                || !("BUSINESS".equals(event.scopeKind()) || "UNRESOLVED".equals(event.scopeKind()))
                || blank(event.questionTemplateId()) || !hash(event.templateSha256()) || event.catalogSha256() != null) {
            throw new IllegalStateException("Incomplete unified questionnaire scope");
        }
        RuntimeConfig runtime = resolve(event.channelId())
                .orElseThrow(() -> new IllegalStateException("Unified questionnaire is unavailable or has changed"));
        if (!runtime.questionTemplateId().equals(event.questionTemplateId())
                || !runtime.templateSha256().equals(event.templateSha256())) {
            throw new IllegalStateException("Unified questionnaire version mismatch; restart questionnaire");
        }

        Map<String, Object> businessQuestion = QuestionnaireFlowContract.questions(runtime.template()).stream()
                .filter(question -> "business".equals(QuestionnaireFlowContract.key(question)))
                .findFirst().orElse(null);
        ConversationTicketCreatedEvent.TicketAttributePayload answer = businessAnswer(event.attributes(), businessQuestion);
        if ("UNRESOLVED".equals(event.scopeKind())) {
            if (event.businessId() != null || answer != null || !blank(event.business())) {
                throw new IllegalStateException("Unresolved questionnaire ticket must not contain a business");
            }
            return;
        }
        if (event.businessId() == null || event.businessId() <= 0 || answer == null
                || !Long.toString(event.businessId()).equals(answer.valueId())) {
            throw new IllegalStateException("Questionnaire business identity is invalid");
        }
        BusinessOption selected = runtime.businesses().stream()
                .filter(option -> option.id() == event.businessId()).findFirst()
                .orElseThrow(() -> new IllegalStateException("Questionnaire business is not bound to the channel"));
        if (!selected.displayName().equals(answer.valueLabel()) || !selected.displayName().equals(event.business())) {
            throw new IllegalStateException("Questionnaire business label is invalid");
        }
    }

    private ConversationTicketCreatedEvent.TicketAttributePayload businessAnswer(
            List<ConversationTicketCreatedEvent.TicketAttributePayload> attributes,
            Map<String, Object> businessQuestion) {
        if (businessQuestion == null) {
            return null;
        }
        List<ConversationTicketCreatedEvent.TicketAttributePayload> matches = new ArrayList<>();
        if (attributes != null) {
            for (ConversationTicketCreatedEvent.TicketAttributePayload attribute : attributes) {
                if (attribute != null && text(businessQuestion.get("id")).equals(attribute.questionId())
                        && "business".equals(attribute.attributeKey())) {
                    matches.add(attribute);
                }
            }
        }
        if (matches.size() > 1) {
            throw new IllegalStateException("Questionnaire has more than one business answer");
        }
        if (matches.isEmpty()) {
            return null;
        }
        ConversationTicketCreatedEvent.TicketAttributePayload answer = matches.get(0);
        if (!"select".equals(answer.inputType()) || blank(answer.valueId()) || blank(answer.valueLabel())
                || answer.valueText() == null || !answer.valueLabel().equals(answer.valueText())) {
            throw new IllegalStateException("Questionnaire business answer shape is invalid");
        }
        return answer;
    }

    private List<BusinessOption> activeBusinesses(long channelId) {
        return jdbc.query("""
                SELECT business.id, business.display_name
                  FROM business_channel_bindings binding
                  JOIN businesses business ON business.id = binding.business_id
                 WHERE binding.channel_id = ? AND binding.is_active = TRUE
                   AND business.status = 'active'
                 ORDER BY business.display_name, business.id
                """, (rs, row) -> new BusinessOption(rs.getLong("id"), rs.getString("display_name")), channelId);
    }

    /**
     * Keeps the location hierarchy aligned with the canonical answers emitted by the business question.
     *
     * <p>The location integration historically used its own display spelling for a business. A difference
     * in letter case must not make the next location question empty after a client has selected the
     * canonical business from the questionnaire. The returned tree contains only businesses bound to the
     * channel and uses their canonical display names as top-level keys.</p>
     */
    static Map<String, Object> locationTreeForBusinesses(Map<String, Object> source,
                                                          List<BusinessOption> businesses) {
        Map<String, String> canonicalNames = new LinkedHashMap<>();
        if (businesses != null) {
            for (BusinessOption business : businesses) {
                if (business == null || blank(business.displayName())) {
                    continue;
                }
                canonicalNames.putIfAbsent(normalizedBusinessName(business.displayName()), business.displayName().trim());
            }
        }
        if (source == null || source.isEmpty() || canonicalNames.isEmpty()) {
            return new LinkedHashMap<>();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            String canonicalName = canonicalNames.get(normalizedBusinessName(entry.getKey()));
            if (canonicalName == null) {
                continue;
            }
            result.merge(canonicalName, entry.getValue(), UnifiedChannelQuestionnaireService::mergeLocationNode);
        }
        return result;
    }

    private static String normalizedBusinessName(String value) {
        if (blank(value)) {
            return "";
        }
        return Normalizer.normalize(value, Normalizer.Form.NFKC).trim().toLowerCase(Locale.ROOT);
    }

    private static Object mergeLocationNode(Object current, Object additional) {
        if (!(current instanceof Map<?, ?> currentMap) || !(additional instanceof Map<?, ?> additionalMap)) {
            return current;
        }
        Map<String, Object> merged = new LinkedHashMap<>();
        currentMap.forEach((key, value) -> {
            if (key != null) {
                merged.put(key.toString(), value);
            }
        });
        additionalMap.forEach((key, value) -> {
            if (key != null) {
                merged.merge(key.toString(), value, UnifiedChannelQuestionnaireService::mergeLocationNode);
            }
        });
        return merged;
    }

    @SuppressWarnings("unchecked")
    private Optional<Map<String, Object>> findTemplate(Map<String, Object> botSettings, String id) {
        Object raw = botSettings.get("question_templates");
        if (!(raw instanceof List<?> templates)) {
            return Optional.empty();
        }
        List<Map<String, Object>> matches = new ArrayList<>();
        for (Object item : templates) {
            if (item instanceof Map<?, ?> template && id.equals(text(template.get("id")))) {
                matches.add(copy((Map<?, ?>) template));
            }
        }
        return matches.size() == 1 ? Optional.of(matches.get(0)) : Optional.empty();
    }

    static String fingerprint(ObjectMapper mapper, Map<String, Object> template) {
        try {
            // A template is shared between separately started JVMs (bot runner and ops worker).
            // Its version must be structural: iteration order of Map.of()/HashMap must never
            // change the version of the same questionnaire.
            byte[] canonical = mapper.writer()
                .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                .writeValueAsBytes(template);
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
        } catch (NoSuchAlgorithmException | JsonProcessingException ex) {
            throw new IllegalStateException("Cannot fingerprint unified questionnaire", ex);
        }
    }

    private Map<String, Object> copy(Map<?, ?> source) {
        return mapper.convertValue(source, new TypeReference<LinkedHashMap<String, Object>>() {});
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString().strip();
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean hash(String value) {
        return value != null && value.matches("[a-f0-9]{64}");
    }

    private record ChannelSelection(long id, String templateId) {
    }

    public record BusinessOption(long id, String displayName) {
    }

    public record RuntimeConfig(long channelId, String questionTemplateId, String templateSha256,
                                Map<String, Object> botSettings, Map<String, Object> template,
                                List<BusinessOption> businesses) {
    }
}
