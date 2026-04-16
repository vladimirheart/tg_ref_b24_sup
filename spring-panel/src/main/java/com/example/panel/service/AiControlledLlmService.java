package com.example.panel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class AiControlledLlmService {

    private static final Pattern TOKEN_SPLIT = Pattern.compile("[^\\p{IsAlphabetic}\\p{IsDigit}]+");
    private static final Pattern MONEY_PROMISE_PATTERN = Pattern.compile("(?iu)(вернем деньги|refund|компенсац|гарантир(?:уем|ую)|обязательно вернем)");
    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+");
    private static final Pattern PHONE_PATTERN = Pattern.compile("(?iu)(\\+?[0-9][0-9\\-()\\s]{8,})");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("(?iu)[a-z0-9._%+-]+@[a-z0-9.-]+\\.[a-z]{2,}");
    private static final Pattern CARD_PATTERN = Pattern.compile("(?iu)\\b\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}[\\s-]?\\d{4}\\b");
    private static final Pattern PASSPORT_PATTERN = Pattern.compile("(?iu)(паспорт|passport)");
    private static final Set<String> STOP = Set.of(
            "и", "в", "на", "не", "что", "как", "для", "или", "по", "из", "к", "у", "о", "об",
            "the", "a", "an", "to", "of", "in", "on", "for", "and", "or", "is", "are", "be"
    );

    private final SharedConfigService sharedConfigService;
    private final AiIntentService aiIntentService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public AiControlledLlmService(SharedConfigService sharedConfigService,
                                  AiIntentService aiIntentService,
                                  ObjectMapper objectMapper) {
        this.sharedConfigService = sharedConfigService;
        this.aiIntentService = aiIntentService;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public IntentAnalysis analyzeIntent(String ticketId,
                                        String message,
                                        AiIntentService.IntentMatch deterministicMatch) {
        RuntimeConfig config = resolveConfig();
        if (!config.enabled() || !config.roles().contains(Role.PARSER)) {
            return fallbackIntentAnalysis(deterministicMatch, config, "disabled");
        }
        try {
            if ("mock".equals(config.provider())) {
                return mockIntentAnalysis(deterministicMatch, config);
            }
            if ("openai_compatible".equals(config.provider())) {
                String response = requestOpenAiCompatible(
                        config,
                        List.of(
                                messageOf("system", """
                                        You are an intent parser for a customer-support agent.
                                        Return compact JSON only with fields:
                                        intent_key, confidence, slots, rationale.
                                        Never invent policy decisions.
                                        """),
                                messageOf("user", """
                                        Ticket: %s
                                        Message: %s
                                        Deterministic intent: %s
                                        Deterministic slots: %s
                                        """.formatted(
                                        trim(ticketId),
                                        safe(message),
                                        deterministicMatch != null ? safe(deterministicMatch.intentKey()) : "",
                                        deterministicMatch != null ? safe(deterministicMatch.slotsJson()) : "{}"
                                ))
                        ),
                        220
                );
                Map<String, Object> json = parseJsonObject(response);
                String intentKey = trim(stringValue(json.get("intent_key")));
                Double confidence = parseDouble(json.get("confidence"));
                Map<String, String> slots = castStringMap(json.get("slots"));
                return new IntentAnalysis(
                        deterministicMatch != null ? deterministicMatch.intentKey() : null,
                        deterministicMatch != null ? deterministicMatch.slots() : Map.of(),
                        intentKey,
                        slots,
                        confidence != null ? clamp01(confidence) : 0d,
                        true,
                        config.provider(),
                        config.model(),
                        "ok",
                        null,
                        resolveRollout(ticketId, false).variant()
                );
            }
        } catch (Exception ex) {
            return fallbackIntentAnalysis(deterministicMatch, config, "error:" + cut(ex.getMessage(), 160));
        }
        return fallbackIntentAnalysis(deterministicMatch, config, "unsupported_provider");
    }

    public RewriteResult rewriteQuery(String ticketId, String message) {
        RuntimeConfig config = resolveConfig();
        RolloutDecision rollout = resolveRollout(ticketId, false);
        String original = trim(message);
        if (!StringUtils.hasText(original) || !config.enabled() || !config.roles().contains(Role.REWRITE)) {
            return new RewriteResult(original, null, false, config.provider(), config.model(), "disabled", null, rollout.variant());
        }
        try {
            String rewritten;
            if ("mock".equals(config.provider())) {
                rewritten = mockRewrite(original);
            } else if ("openai_compatible".equals(config.provider())) {
                rewritten = requestOpenAiCompatible(
                        config,
                        List.of(
                                messageOf("system", """
                                        Rewrite the support message into a compact retrieval query.
                                        Preserve order ids, businesses, locations, channels and money amounts.
                                        Return plain text only, no explanations.
                                        """),
                                messageOf("user", original)
                        ),
                        120
                );
            } else {
                return new RewriteResult(original, null, false, config.provider(), config.model(), "unsupported_provider", null, rollout.variant());
            }
            String normalizedRewrite = trim(rewritten);
            if (!StringUtils.hasText(normalizedRewrite)) {
                return new RewriteResult(original, null, false, config.provider(), config.model(), "empty_rewrite", null, rollout.variant());
            }
            if (!preservesStructuredTokens(original, normalizedRewrite)) {
                return new RewriteResult(original, null, false, config.provider(), config.model(), "guard:missing_slots", "missing_slots", rollout.variant());
            }
            String effective = original.equalsIgnoreCase(normalizedRewrite) ? original : original + "\n" + normalizedRewrite;
            return new RewriteResult(effective, normalizedRewrite, true, config.provider(), config.model(), "ok", null, rollout.variant());
        } catch (Exception ex) {
            return new RewriteResult(original, null, false, config.provider(), config.model(), "error:" + cut(ex.getMessage(), 160), null, rollout.variant());
        }
    }

    public TextResult composeReply(String ticketId,
                                   String clientMessage,
                                   String evidenceText,
                                   String sourceRef,
                                   String intentKey,
                                   boolean autoReplyRequested) {
        RuntimeConfig config = resolveConfig();
        RolloutDecision rollout = resolveRollout(ticketId, autoReplyRequested);
        if (!config.enabled() || !config.roles().contains(Role.COMPOSER)) {
            return TextResult.disabled(config.provider(), config.model(), rollout.variant(), "disabled");
        }
        if (autoReplyRequested && !rollout.allowAutoReplyLlm()) {
            return TextResult.disabled(config.provider(), config.model(), rollout.variant(), "rollout_assist_only");
        }
        String evidence = trim(evidenceText);
        if (!StringUtils.hasText(evidence)) {
            return TextResult.disabled(config.provider(), config.model(), rollout.variant(), "no_evidence");
        }
        try {
            String candidate;
            if ("mock".equals(config.provider())) {
                candidate = mockCompose(evidence);
            } else if ("openai_compatible".equals(config.provider())) {
                candidate = requestOpenAiCompatible(
                        config,
                        List.of(
                                messageOf("system", """
                                        You are a constrained support response composer.
                                        Use only the provided evidence.
                                        Never invent policies, links, refunds, compensation or personal-data instructions.
                                        Keep the answer concise and practical.
                                        """),
                                messageOf("user", """
                                        Intent: %s
                                        Client message: %s
                                        Evidence: %s
                                        """.formatted(safe(intentKey), safe(clientMessage), safe(evidence)))
                        ),
                        autoReplyRequested ? 220 : 260
                );
            } else {
                return TextResult.disabled(config.provider(), config.model(), rollout.variant(), "unsupported_provider");
            }
            GuardResult guard = applyOutputGuard(candidate, evidence, sourceRef, intentKey, autoReplyRequested, config);
            if (!guard.allowed()) {
                return TextResult.disabled(config.provider(), config.model(), rollout.variant(), "guard:" + guard.reason(), guard.reason());
            }
            return new TextResult(cleanText(candidate), true, config.provider(), config.model(), rollout.variant(), "ok", null);
        } catch (Exception ex) {
            return TextResult.disabled(config.provider(), config.model(), rollout.variant(), "error:" + cut(ex.getMessage(), 160));
        }
    }

    public TextResult explainSuggestion(String ticketId,
                                        String clientMessage,
                                        String evidenceText,
                                        String source,
                                        String trustLevel,
                                        String intentKey,
                                        int evidenceCount) {
        RuntimeConfig config = resolveConfig();
        RolloutDecision rollout = resolveRollout(ticketId, false);
        if (!config.enabled() || !config.roles().contains(Role.EXPLAINER)) {
            return TextResult.disabled(config.provider(), config.model(), rollout.variant(), "disabled");
        }
        String evidence = trim(evidenceText);
        if (!StringUtils.hasText(evidence)) {
            return TextResult.disabled(config.provider(), config.model(), rollout.variant(), "no_evidence");
        }
        try {
            String candidate;
            if ("mock".equals(config.provider())) {
                candidate = "Подсказка собрана по подтвержденному evidence. intent=" + safe(intentKey)
                        + ", source=" + safe(source) + ", trust=" + safe(trustLevel)
                        + ", support=" + Math.max(1, evidenceCount) + ".";
            } else if ("openai_compatible".equals(config.provider())) {
                candidate = requestOpenAiCompatible(
                        config,
                        List.of(
                                messageOf("system", """
                                        Explain to an operator why this suggestion was selected.
                                        Reference only the supplied evidence metadata.
                                        Keep the answer under 2 sentences.
                                        """),
                                messageOf("user", """
                                        Client message: %s
                                        Intent: %s
                                        Source: %s
                                        Trust: %s
                                        Support count: %s
                                        Evidence: %s
                                        """.formatted(
                                        safe(clientMessage),
                                        safe(intentKey),
                                        safe(source),
                                        safe(trustLevel),
                                        evidenceCount,
                                        safe(evidence)
                                ))
                        ),
                        120
                );
            } else {
                return TextResult.disabled(config.provider(), config.model(), rollout.variant(), "unsupported_provider");
            }
            GuardResult guard = applyOutputGuard(candidate, evidence, null, intentKey, false, config);
            if (!guard.allowed()) {
                return TextResult.disabled(config.provider(), config.model(), rollout.variant(), "guard:" + guard.reason(), guard.reason());
            }
            return new TextResult(cleanText(candidate), true, config.provider(), config.model(), rollout.variant(), "ok", null);
        } catch (Exception ex) {
            return TextResult.disabled(config.provider(), config.model(), rollout.variant(), "error:" + cut(ex.getMessage(), 160));
        }
    }

    public RolloutDecision resolveRollout(String ticketId, boolean autoReplyRequested) {
        RuntimeConfig config = resolveConfig();
        if (!config.enabled()) {
            return new RolloutDecision(false, false, "disabled");
        }
        String mode = trim(config.rolloutMode());
        if (!autoReplyRequested) {
            return new RolloutDecision(true, false, firstNonBlank(mode, "assist_only"));
        }
        if ("selective_auto_reply".equals(mode)) {
            return new RolloutDecision(true, true, "selective_auto_reply");
        }
        if ("assist_only".equals(mode)) {
            return new RolloutDecision(true, false, "assist_only");
        }
        int bucket = stablePercent(ticketId);
        boolean allow = bucket < config.rolloutPercent();
        return new RolloutDecision(true, allow, allow ? "selective_auto_reply" : "assist_only");
    }

    private IntentAnalysis fallbackIntentAnalysis(AiIntentService.IntentMatch deterministicMatch,
                                                  RuntimeConfig config,
                                                  String reason) {
        return new IntentAnalysis(
                deterministicMatch != null ? deterministicMatch.intentKey() : null,
                deterministicMatch != null ? deterministicMatch.slots() : Map.of(),
                deterministicMatch != null ? deterministicMatch.intentKey() : null,
                deterministicMatch != null ? deterministicMatch.slots() : Map.of(),
                deterministicMatch != null ? clamp01(deterministicMatch.confidence()) : 0d,
                false,
                config.provider(),
                config.model(),
                reason,
                null,
                resolveRollout(null, false).variant()
        );
    }

    private IntentAnalysis mockIntentAnalysis(AiIntentService.IntentMatch deterministicMatch,
                                              RuntimeConfig config) {
        return new IntentAnalysis(
                deterministicMatch != null ? deterministicMatch.intentKey() : null,
                deterministicMatch != null ? deterministicMatch.slots() : Map.of(),
                deterministicMatch != null ? deterministicMatch.intentKey() : null,
                deterministicMatch != null ? deterministicMatch.slots() : Map.of(),
                deterministicMatch != null ? clamp01(Math.max(0.55d, deterministicMatch.confidence())) : 0.55d,
                true,
                config.provider(),
                config.model(),
                "ok",
                null,
                resolveRollout(null, false).variant()
        );
    }

    private String mockRewrite(String message) {
        AiIntentService.IntentMatch intentMatch = aiIntentService.extract(message);
        StringBuilder out = new StringBuilder();
        if (intentMatch != null && StringUtils.hasText(intentMatch.intentKey())) {
            out.append("intent=").append(intentMatch.intentKey());
        }
        if (intentMatch != null && intentMatch.slots() != null) {
            for (Map.Entry<String, String> entry : intentMatch.slots().entrySet()) {
                if (!StringUtils.hasText(entry.getValue())) {
                    continue;
                }
                if (!out.isEmpty()) {
                    out.append(' ');
                }
                out.append(entry.getKey()).append('=').append(entry.getValue());
            }
        }
        Set<String> tokens = tokenize(message);
        int added = 0;
        for (String token : tokens) {
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(token);
            added++;
            if (added >= 6) {
                break;
            }
        }
        return out.toString();
    }

    private String mockCompose(String evidence) {
        String cleaned = cleanText(evidence);
        List<String> steps = splitIntoSteps(cleaned, 3);
        StringBuilder reply = new StringBuilder();
        reply.append("Коротко: ").append(cut(firstSentence(cleaned), 220)).append("\n\n");
        reply.append("Что сделать:\n");
        if (steps.isEmpty()) {
            reply.append("1. ").append(cut(cleaned, 280)).append("\n");
        } else {
            for (int i = 0; i < steps.size(); i++) {
                reply.append(i + 1).append(". ").append(steps.get(i)).append("\n");
            }
        }
        reply.append("\nЕсли проблема останется, напишите, что именно не сработало.");
        return reply.toString();
    }

    private GuardResult applyOutputGuard(String text,
                                         String evidence,
                                         String sourceRef,
                                         String intentKey,
                                         boolean autoReplyRequested,
                                         RuntimeConfig config) {
        String candidate = trim(text);
        if (!StringUtils.hasText(candidate)) {
            return new GuardResult(false, "empty_output");
        }
        if (!config.outputGuardEnabled()) {
            return new GuardResult(true, null);
        }
        String normalizedEvidence = normalize(evidence);
        String normalizedCandidate = normalize(candidate);
        if (autoReplyRequested && "refund_request".equals(trim(intentKey))) {
            return new GuardResult(false, "policy_boundary_refund");
        }
        if (MONEY_PROMISE_PATTERN.matcher(normalizedCandidate).find() && !MONEY_PROMISE_PATTERN.matcher(normalizedEvidence).find()) {
            return new GuardResult(false, "money_promise_without_evidence");
        }
        if (URL_PATTERN.matcher(candidate).find() && !URL_PATTERN.matcher(evidence != null ? evidence : "").find() && !StringUtils.hasText(sourceRef)) {
            return new GuardResult(false, "unverified_link");
        }
        if (containsPii(candidate) && !containsPii(evidence)) {
            return new GuardResult(false, "pii_leak");
        }
        if (lexicalOverlap(candidate, evidence) < 0.10d) {
            return new GuardResult(false, "low_evidence_overlap");
        }
        return new GuardResult(true, null);
    }

    private boolean preservesStructuredTokens(String original, String rewritten) {
        AiIntentService.IntentMatch source = aiIntentService.extract(original);
        if (source == null || source.slots().isEmpty()) {
            return true;
        }
        String normalizedRewrite = normalize(rewritten);
        for (String value : source.slots().values()) {
            String normalized = normalize(value);
            if (StringUtils.hasText(normalized) && !normalizedRewrite.contains(normalized)) {
                return false;
            }
        }
        return true;
    }

    private RuntimeConfig resolveConfig() {
        Map<String, Object> settings = sharedConfigService.loadSettings();
        Map<String, Object> dialogConfig = settings.get("dialog_config") instanceof Map<?, ?> raw ? castObjectMap(raw) : Map.of();
        boolean enabled = parseBoolean(dialogConfig.get("ai_agent_llm_enabled"), false);
        String provider = normalizeConfig(dialogConfig.get("ai_agent_llm_provider"), enabled ? "mock" : "disabled");
        String endpoint = trim(stringValue(dialogConfig.get("ai_agent_llm_endpoint")));
        String model = trim(stringValue(dialogConfig.get("ai_agent_llm_model")));
        int timeoutMs = parseInt(dialogConfig.get("ai_agent_llm_timeout_ms"), 4000, 500, 20000);
        Set<Role> roles = parseRoles(dialogConfig.get("ai_agent_llm_roles"));
        String rolloutMode = normalizeConfig(dialogConfig.get("ai_agent_llm_rollout_mode"), "assist_only");
        int rolloutPercent = parseInt(dialogConfig.get("ai_agent_llm_rollout_percent"), 25, 0, 100);
        boolean outputGuardEnabled = parseBoolean(dialogConfig.get("ai_agent_llm_output_guard_enabled"), true);
        return new RuntimeConfig(enabled, provider, endpoint, model, timeoutMs, roles, rolloutMode, rolloutPercent, outputGuardEnabled);
    }

    private Set<Role> parseRoles(Object rawValue) {
        Set<Role> roles = new LinkedHashSet<>();
        if (rawValue instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                addRole(roles, item != null ? String.valueOf(item) : null);
            }
        } else {
            String text = trim(stringValue(rawValue));
            if (StringUtils.hasText(text)) {
                for (String part : text.split(",")) {
                    addRole(roles, part);
                }
            }
        }
        if (roles.isEmpty()) {
            roles.addAll(Set.of(Role.PARSER, Role.REWRITE, Role.COMPOSER, Role.EXPLAINER));
        }
        return roles;
    }

    private void addRole(Set<Role> roles, String rawRole) {
        String normalized = normalize(rawRole);
        switch (normalized) {
            case "parser", "intent_parser" -> roles.add(Role.PARSER);
            case "rewrite", "query_rewrite" -> roles.add(Role.REWRITE);
            case "composer", "response_composer" -> roles.add(Role.COMPOSER);
            case "explainer", "operator_explainer" -> roles.add(Role.EXPLAINER);
            default -> {
            }
        }
    }

    private String requestOpenAiCompatible(RuntimeConfig config,
                                           List<Map<String, Object>> messages,
                                           int maxTokens) throws IOException, InterruptedException {
        String endpoint = firstNonBlank(config.endpoint(), "https://api.openai.com/v1/chat/completions");
        String apiKey = firstNonBlank(
                System.getenv("OPENAI_API_KEY"),
                System.getenv("AI_AGENT_LLM_API_KEY")
        );
        if (!StringUtils.hasText(apiKey) || !StringUtils.hasText(config.model())) {
            throw new IOException("missing_api_key_or_model");
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("model", config.model());
        payload.put("messages", messages);
        payload.put("temperature", 0.1d);
        payload.put("max_tokens", maxTokens);
        String body = objectMapper.writeValueAsString(payload);
        HttpRequest request = HttpRequest.newBuilder(URI.create(endpoint))
                .timeout(Duration.ofMillis(config.timeoutMs()))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IOException("http_" + response.statusCode());
        }
        JsonNode root = objectMapper.readTree(response.body());
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (!content.isTextual()) {
            throw new IOException("empty_content");
        }
        return content.asText();
    }

    private Map<String, Object> messageOf(String role, String content) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", role);
        message.put("content", content);
        return message;
    }

    private Map<String, Object> parseJsonObject(String value) throws IOException {
        String normalized = trim(value);
        if (!StringUtils.hasText(normalized)) {
            return Map.of();
        }
        if (normalized.startsWith("```")) {
            normalized = normalized.replaceAll("(?s)^```(?:json)?\\s*", "").replaceAll("\\s*```$", "");
        }
        return objectMapper.readValue(normalized, new TypeReference<LinkedHashMap<String, Object>>() {
        });
    }

    private Map<String, String> castStringMap(Object rawValue) {
        if (!(rawValue instanceof Map<?, ?> rawMap)) {
            return Map.of();
        }
        Map<String, String> result = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> {
            String normalizedKey = trim(key != null ? String.valueOf(key) : null);
            String normalizedValue = trim(value != null ? String.valueOf(value) : null);
            if (normalizedKey != null && normalizedValue != null) {
                result.put(normalizedKey, normalizedValue);
            }
        });
        return result;
    }

    private Map<String, Object> castObjectMap(Map<?, ?> rawMap) {
        Map<String, Object> result = new LinkedHashMap<>();
        rawMap.forEach((key, value) -> {
            if (key != null) {
                result.put(String.valueOf(key), value);
            }
        });
        return result;
    }

    private boolean containsPii(String value) {
        String text = value != null ? value : "";
        return PHONE_PATTERN.matcher(text).find()
                || EMAIL_PATTERN.matcher(text).find()
                || CARD_PATTERN.matcher(text).find()
                || PASSPORT_PATTERN.matcher(text).find();
    }

    private double lexicalOverlap(String left, String right) {
        Set<String> first = tokenize(left);
        Set<String> second = tokenize(right);
        if (first.isEmpty() || second.isEmpty()) {
            return 0d;
        }
        int overlap = 0;
        for (String token : first) {
            if (second.contains(token)) {
                overlap++;
            }
        }
        return overlap / (double) Math.max(1, first.size());
    }

    private Set<String> tokenize(String value) {
        String normalized = normalize(value);
        if (!StringUtils.hasText(normalized)) {
            return Set.of();
        }
        Set<String> tokens = new LinkedHashSet<>();
        for (String part : TOKEN_SPLIT.split(normalized)) {
            String token = trim(part);
            if (!StringUtils.hasText(token) || token.length() < 2 || STOP.contains(token)) {
                continue;
            }
            tokens.add(token);
        }
        return tokens;
    }

    private List<String> splitIntoSteps(String body, int limit) {
        List<String> out = new ArrayList<>();
        if (!StringUtils.hasText(body)) {
            return out;
        }
        for (String chunk : body.split("(?<=[.!?])\\s+|\\n+|;\\s*")) {
            String step = trim(chunk);
            if (!StringUtils.hasText(step)) {
                continue;
            }
            out.add(cut(step, 280));
            if (out.size() >= limit) {
                break;
            }
        }
        return out;
    }

    private String firstSentence(String body) {
        String normalized = trim(body);
        if (!StringUtils.hasText(normalized)) {
            return "";
        }
        Matcher matcher = Pattern.compile("^(.{1,220}?[.!?])(?:\\s|$)").matcher(normalized);
        if (matcher.find()) {
            return trim(matcher.group(1));
        }
        return cut(normalized, 220);
    }

    private String cleanText(String value) {
        String normalized = trim(value);
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        return normalized.replace('\r', '\n').replaceAll("\\n{3,}", "\n\n");
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replace('\u0451', '\u0435').replaceAll("\\s+", " ").trim();
    }

    private String normalizeConfig(Object value, String fallback) {
        String normalized = normalize(stringValue(value));
        return StringUtils.hasText(normalized) ? normalized : fallback;
    }

    private boolean parseBoolean(Object value, boolean fallback) {
        if (value instanceof Boolean rawBoolean) {
            return rawBoolean;
        }
        String normalized = normalize(stringValue(value));
        if (!StringUtils.hasText(normalized)) {
            return fallback;
        }
        return !"false".equals(normalized) && !"0".equals(normalized) && !"off".equals(normalized) && !"no".equals(normalized);
    }

    private int parseInt(Object value, int fallback, int min, int max) {
        try {
            int parsed = Integer.parseInt(String.valueOf(value).trim());
            return Math.max(min, Math.min(max, parsed));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private Double parseDouble(Object value) {
        try {
            return value != null ? Double.parseDouble(String.valueOf(value).trim()) : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private int stablePercent(String seed) {
        String normalized = firstNonBlank(trim(seed), "default");
        return Math.floorMod(normalized.hashCode(), 100);
    }

    private double clamp01(double value) {
        return Math.max(0d, Math.min(1d, value));
    }

    private String cut(String value, int max) {
        String normalized = trim(value);
        if (normalized == null) {
            return null;
        }
        return normalized.length() <= max ? normalized : normalized.substring(0, Math.max(0, max - 3)) + "...";
    }

    private String trim(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private String stringValue(Object value) {
        return value != null ? String.valueOf(value) : null;
    }

    private String safe(String value) {
        return value != null ? value : "";
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = trim(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private enum Role {
        PARSER,
        REWRITE,
        COMPOSER,
        EXPLAINER
    }

    private record RuntimeConfig(boolean enabled,
                                 String provider,
                                 String endpoint,
                                 String model,
                                 int timeoutMs,
                                 Set<Role> roles,
                                 String rolloutMode,
                                 int rolloutPercent,
                                 boolean outputGuardEnabled) {
    }

    private record GuardResult(boolean allowed, String reason) {
    }

    public record RolloutDecision(boolean llmEnabled,
                                  boolean allowAutoReplyLlm,
                                  String variant) {
    }

    public record IntentAnalysis(String deterministicIntentKey,
                                 Map<String, String> deterministicSlots,
                                 String llmIntentKey,
                                 Map<String, String> llmSlots,
                                 double llmConfidence,
                                 boolean usedLlm,
                                 String provider,
                                 String model,
                                 String reason,
                                 String guardReason,
                                 String variant) {
    }

    public record RewriteResult(String effectiveQuery,
                                String rewrittenQuery,
                                boolean usedLlm,
                                String provider,
                                String model,
                                String reason,
                                String guardReason,
                                String variant) {
    }

    public record TextResult(String text,
                             boolean usedLlm,
                             String provider,
                             String model,
                             String variant,
                             String reason,
                             String guardReason) {
        public static TextResult disabled(String provider, String model, String variant, String reason) {
            return new TextResult(null, false, provider, model, variant, reason, null);
        }

        public static TextResult disabled(String provider, String model, String variant, String reason, String guardReason) {
            return new TextResult(null, false, provider, model, variant, reason, guardReason);
        }
    }
}
