package com.example.panel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class AiOfflineEvaluationService {

    private static final String DATASET_RESOURCE = "ai/offline-eval-templates.json";

    private final JdbcTemplate jdbcTemplate;
    private final AiIntentService aiIntentService;
    private final AiRetrievalService aiRetrievalService;
    private final SharedConfigService sharedConfigService;
    private final ObjectMapper objectMapper;

    public AiOfflineEvaluationService(JdbcTemplate jdbcTemplate,
                                      AiIntentService aiIntentService,
                                      AiRetrievalService aiRetrievalService,
                                      SharedConfigService sharedConfigService,
                                      ObjectMapper objectMapper) {
        this.jdbcTemplate = jdbcTemplate;
        this.aiIntentService = aiIntentService;
        this.aiRetrievalService = aiRetrievalService;
        this.sharedConfigService = sharedConfigService;
        this.objectMapper = objectMapper;
    }

    @Scheduled(cron = "${panel.ai.offline-eval.cron:0 15 3 * * *}")
    public void runScheduledEvaluation() {
        if (!isOfflineEvalEnabled()) {
            return;
        }
        try {
            runEvaluationNow("scheduler");
        } catch (Exception ignored) {
        }
    }

    public Map<String, Object> runEvaluationNow(String actor) {
        DatasetBundle bundle = loadDatasetBundle();
        List<EvalCase> cases = bundle.cases();
        int total = cases.size();
        int passed = 0;
        int intentPassed = 0;
        int policyPassed = 0;
        int retrievalPassed = 0;
        int confirmationPassed = 0;
        List<Map<String, Object>> failures = new ArrayList<>();

        for (EvalCase evalCase : cases) {
            AiIntentService.IntentMatch intentMatch = aiIntentService.extract(evalCase.message());
            AiIntentService.IntentPolicy policy = aiIntentService.resolvePolicy(intentMatch.intentKey());
            AiRetrievalService.RetrievalResult retrievalResult = aiRetrievalService.retrieve(evalCase.ticketId(), evalCase.message(), 3);
            AiRetrievalService.Candidate top = retrievalResult.candidates().isEmpty() ? null : retrievalResult.candidates().get(0);

            boolean intentOk = eq(evalCase.expectedIntent(), intentMatch.intentKey());
            boolean policyOk = evalCase.expectedAutoReplyAllowed() == policy.autoReplyAllowed()
                    && evalCase.expectedRequiresOperator() == policy.requiresOperator();
            boolean retrievalOk = top != null && eq(evalCase.expectedIntent(), top.intentKey());
            boolean confirmationOk = !evalCase.expectConfirmed() || retrievalResult.consistency().autoReplyAllowed();
            boolean casePassed = intentOk && policyOk && retrievalOk && confirmationOk;

            if (intentOk) {
                intentPassed++;
            }
            if (policyOk) {
                policyPassed++;
            }
            if (retrievalOk) {
                retrievalPassed++;
            }
            if (confirmationOk) {
                confirmationPassed++;
            }
            if (casePassed) {
                passed++;
            } else if (failures.size() < 25) {
                Map<String, Object> failure = new LinkedHashMap<>();
                failure.put("case_id", evalCase.caseId());
                failure.put("message", evalCase.message());
                failure.put("expected_intent", evalCase.expectedIntent());
                failure.put("actual_intent", intentMatch.intentKey());
                failure.put("expected_auto_reply_allowed", evalCase.expectedAutoReplyAllowed());
                failure.put("actual_auto_reply_allowed", policy.autoReplyAllowed());
                failure.put("expected_requires_operator", evalCase.expectedRequiresOperator());
                failure.put("actual_requires_operator", policy.requiresOperator());
                failure.put("expected_confirmed", evalCase.expectConfirmed());
                failure.put("actual_confirmed", retrievalResult.consistency().autoReplyAllowed());
                failure.put("top_candidate_intent", top != null ? top.intentKey() : null);
                failure.put("top_candidate_score", top != null ? top.score() : null);
                failure.put("consistency_reason", retrievalResult.consistency().reason());
                failures.add(failure);
            }
        }

        double passRate = safeRate(passed, total);
        double intentAccuracy = safeRate(intentPassed, total);
        double policyAccuracy = safeRate(policyPassed, total);
        double retrievalHitRate = safeRate(retrievalPassed, total);
        double confirmedReplyRate = safeRate(confirmationPassed, total);
        String detailsJson = toJson(Map.of(
                "dataset_version", bundle.datasetVersion(),
                "generated_at", Instant.now().toString(),
                "sample_failures", failures
        ));

        storeRun(bundle.datasetVersion(), actor, total, passed, intentAccuracy, policyAccuracy, retrievalHitRate, confirmedReplyRate, detailsJson);
        Map<String, Object> latest = loadLatestRun();
        if (!latest.isEmpty()) {
            latest.put("dataset_cases", total);
        }
        return latest;
    }

    public Map<String, Object> loadLatestRun() {
        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                    """
                    SELECT id, dataset_version, actor, cases_total, cases_passed,
                           intent_accuracy, policy_accuracy, retrieval_hit_rate, confirmed_reply_rate,
                           details_json, created_at
                      FROM ai_agent_offline_eval_run
                     ORDER BY id DESC
                     LIMIT 1
                    """
            );
            if (rows.isEmpty()) {
                DatasetBundle bundle = loadDatasetBundle();
                return Map.of(
                        "available", false,
                        "dataset_version", bundle.datasetVersion(),
                        "dataset_cases", bundle.cases().size()
                );
            }
            Map<String, Object> row = new LinkedHashMap<>(rows.get(0));
            row.put("available", true);
            row.put("details", parseJson(row.get("details_json")));
            return row;
        } catch (Exception ex) {
            DatasetBundle bundle = loadDatasetBundle();
            return Map.of(
                    "available", false,
                    "dataset_version", bundle.datasetVersion(),
                    "dataset_cases", bundle.cases().size()
            );
        }
    }

    public Map<String, Object> loadDatasetOverview() {
        DatasetBundle bundle = loadDatasetBundle();
        return Map.of(
                "dataset_version", bundle.datasetVersion(),
                "cases_total", bundle.cases().size(),
                "templates_total", bundle.templateCount()
        );
    }

    private void storeRun(String datasetVersion,
                          String actor,
                          int casesTotal,
                          int casesPassed,
                          double intentAccuracy,
                          double policyAccuracy,
                          double retrievalHitRate,
                          double confirmedReplyRate,
                          String detailsJson) {
        try {
            jdbcTemplate.update(
                    """
                    INSERT INTO ai_agent_offline_eval_run(
                        dataset_version, actor, cases_total, cases_passed,
                        intent_accuracy, policy_accuracy, retrieval_hit_rate, confirmed_reply_rate,
                        details_json, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """,
                    trim(datasetVersion),
                    trim(actor),
                    casesTotal,
                    casesPassed,
                    intentAccuracy,
                    policyAccuracy,
                    retrievalHitRate,
                    confirmedReplyRate,
                    detailsJson
            );
        } catch (Exception ignored) {
        }
    }

    private DatasetBundle loadDatasetBundle() {
        try (InputStream stream = new ClassPathResource(DATASET_RESOURCE).getInputStream()) {
            Map<String, Object> raw = objectMapper.readValue(stream, new TypeReference<LinkedHashMap<String, Object>>() {
            });
            String version = trim(stringValue(raw.get("dataset_version")));
            List<String> businesses = asStringList(raw.get("businesses"), List.of("блинбери", "сушивесла", "пиццафабрика", "bb"));
            List<String> channels = asStringList(raw.get("channels"), List.of("telegram", "vk", "max", "web_form"));
            List<String> locations = asStringList(raw.get("locations"), List.of("центр", "юг", "север", "мега"));
            List<String> amounts = asStringList(raw.get("amounts"), List.of("499", "799", "1290", "1590"));
            int variantsPerTemplate = parseInt(raw.get("variants_per_template"), 30, 5, 60);
            List<Map<String, Object>> templates = raw.get("templates") instanceof List<?> items ? castList(items) : List.of();
            List<EvalCase> cases = new ArrayList<>();
            for (Map<String, Object> template : templates) {
                String templateId = trim(stringValue(template.get("id")));
                String messageTemplate = trim(stringValue(template.get("message")));
                String expectedIntent = trim(stringValue(template.get("expected_intent")));
                boolean expectedAutoReplyAllowed = parseBoolean(template.get("expected_auto_reply_allowed"), false);
                boolean expectedRequiresOperator = parseBoolean(template.get("expected_requires_operator"), false);
                boolean expectConfirmed = parseBoolean(template.get("expect_confirmed"), false);
                if (!StringUtils.hasText(templateId) || !StringUtils.hasText(messageTemplate) || !StringUtils.hasText(expectedIntent)) {
                    continue;
                }
                int variants = parseInt(template.get("variants"), variantsPerTemplate, 5, 80);
                for (int i = 0; i < variants; i++) {
                    String business = businesses.get(i % businesses.size());
                    String channel = channels.get(i % channels.size());
                    String location = locations.get((i / Math.max(1, channels.size())) % locations.size());
                    String amount = amounts.get(i % amounts.size());
                    String orderId = templateId.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "") + "-" + (100 + i);
                    String message = messageTemplate
                            .replace("{business}", business)
                            .replace("{channel}", channel)
                            .replace("{location}", location)
                            .replace("{amount}", amount)
                            .replace("{order_id}", orderId);
                    cases.add(new EvalCase(
                            templateId + "-" + (i + 1),
                            "offline-" + templateId + "-" + (i + 1),
                            message,
                            expectedIntent,
                            expectedAutoReplyAllowed,
                            expectedRequiresOperator,
                            expectConfirmed
                    ));
                }
            }
            return new DatasetBundle(firstNonBlank(version, "offline-eval-v1"), templates.size(), cases);
        } catch (Exception ex) {
            return new DatasetBundle("offline-eval-fallback", 0, List.of());
        }
    }

    private List<Map<String, Object>> castList(List<?> items) {
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : items) {
            if (item instanceof Map<?, ?> rawMap) {
                Map<String, Object> row = new LinkedHashMap<>();
                rawMap.forEach((key, value) -> {
                    if (key != null) {
                        row.put(String.valueOf(key), value);
                    }
                });
                result.add(row);
            }
        }
        return result;
    }

    private List<String> asStringList(Object rawValue, List<String> fallback) {
        if (!(rawValue instanceof List<?> items)) {
            return fallback;
        }
        List<String> result = new ArrayList<>();
        for (Object item : items) {
            String value = trim(stringValue(item));
            if (value != null) {
                result.add(value);
            }
        }
        return result.isEmpty() ? fallback : result;
    }

    private Map<String, Object> parseJson(Object rawValue) {
        try {
            String value = stringValue(rawValue);
            return StringUtils.hasText(value)
                    ? objectMapper.readValue(value, new TypeReference<LinkedHashMap<String, Object>>() {
                    })
                    : Map.of();
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private boolean isOfflineEvalEnabled() {
        try {
            Map<String, Object> settings = sharedConfigService.loadSettings();
            if (settings.get("dialog_config") instanceof Map<?, ?> dialogConfig) {
                Object raw = dialogConfig.get("ai_agent_offline_eval_enabled");
                return parseBoolean(raw, true);
            }
        } catch (Exception ignored) {
        }
        return true;
    }

    private boolean eq(String left, String right) {
        return normalize(left).equals(normalize(right));
    }

    private double safeRate(int numerator, int denominator) {
        if (denominator <= 0) {
            return 0d;
        }
        return Math.max(0d, Math.min(1d, numerator / (double) denominator));
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

    private String toJson(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return "{}";
        }
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.toLowerCase(Locale.ROOT).replace('\u0451', '\u0435').trim();
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

    public Instant parseInstant(Object value) {
        String raw = trim(stringValue(value));
        if (raw == null) {
            return null;
        }
        try {
            return Instant.parse(raw);
        } catch (DateTimeParseException ignored) {
        }
        try {
            return LocalDateTime.parse(raw.replace(' ', 'T')).toInstant(ZoneOffset.UTC);
        } catch (DateTimeParseException ignored) {
        }
        return null;
    }

    private record DatasetBundle(String datasetVersion,
                                 int templateCount,
                                 List<EvalCase> cases) {
    }

    private record EvalCase(String caseId,
                            String ticketId,
                            String message,
                            String expectedIntent,
                            boolean expectedAutoReplyAllowed,
                            boolean expectedRequiresOperator,
                            boolean expectConfirmed) {
    }
}
