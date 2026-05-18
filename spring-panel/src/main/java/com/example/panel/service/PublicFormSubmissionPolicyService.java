package com.example.panel.service;

import com.example.panel.model.publicform.PublicFormConfig;
import com.example.panel.model.publicform.PublicFormQuestion;
import com.example.panel.model.publicform.PublicFormSubmission;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;

@Service
public class PublicFormSubmissionPolicyService {
    private static final Logger log = LoggerFactory.getLogger(PublicFormSubmissionPolicyService.class);
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PHONE_PATTERN = Pattern.compile("^[+]?[-()\\s0-9]{6,20}$");
    private static final Pattern HTML_TAG_PATTERN = Pattern.compile("(?i)<\\/?[a-z][^>]*>");
    private static final Pattern CONTROL_CHARS_PATTERN = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]");
    private static final int DEFAULT_MESSAGE_MAX_LENGTH = 4000;
    private static final int DEFAULT_ANSWERS_TOTAL_MAX_LENGTH = 6000;
    private static final Set<String> LOCATION_FIELD_IDS = Set.of("business", "location_type", "city", "location_name");

    private final PublicFormRuntimeConfigService runtimeConfigService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient = HttpClient.newBuilder().build();

    public PublicFormSubmissionPolicyService(PublicFormRuntimeConfigService runtimeConfigService,
                                             ObjectMapper objectMapper) {
        this.runtimeConfigService = runtimeConfigService;
        this.objectMapper = objectMapper;
    }

    public PreparedSubmission prepareSubmission(PublicFormConfig config, PublicFormSubmission submission) {
        boolean stripHtmlTags = runtimeConfigService.readDialogConfigBoolean("public_form_strip_html_tags", true);
        PublicFormSubmission normalizedSubmission = normalizeSubmission(submission, stripHtmlTags);
        Map<String, String> answers = sanitizeAnswers(submission != null ? submission.answers() : null, stripHtmlTags);
        enforceCaptcha(config, normalizedSubmission);
        validateSubmission(config, normalizedSubmission, answers);
        return new PreparedSubmission(
                normalizedSubmission,
                answers,
                buildCombinedMessage(config, answers, normalizedSubmission.message()),
                resolveClientName(normalizedSubmission, answers)
        );
    }

    private void validateSubmission(PublicFormConfig config, PublicFormSubmission submission, Map<String, String> answers) {
        if (!StringUtils.hasText(submission.message())) {
            throw new IllegalArgumentException("Опишите проблему");
        }
        int maxLength = runtimeConfigService.readDialogConfigInt("public_form_message_max_length", DEFAULT_MESSAGE_MAX_LENGTH, 300, 20000);
        if (submission.message().trim().length() > maxLength) {
            throw new IllegalArgumentException("Сообщение слишком длинное (макс. " + maxLength + " символов)");
        }
        int answersPayloadMaxLength = runtimeConfigService.readDialogConfigInt(
                "public_form_answers_total_max_length",
                DEFAULT_ANSWERS_TOTAL_MAX_LENGTH,
                200,
                50000
        );
        int answersPayloadLength = answers.values().stream()
                .filter(StringUtils::hasText)
                .mapToInt(String::length)
                .sum();
        if (answersPayloadLength > answersPayloadMaxLength) {
            throw new IllegalArgumentException("Суммарный объём ответов формы превышает лимит "
                    + answersPayloadMaxLength + " символов");
        }
        for (PublicFormQuestion question : config.questions()) {
            String value = answers.get(question.id());
            if (isRequired(question) && !StringUtils.hasText(value)) {
                throw new IllegalArgumentException("Заполните поле: " + questionLabel(question));
            }
            if (!StringUtils.hasText(value)) {
                continue;
            }
            int minLength = metadataInt(question, "minLength", 0);
            int maxQuestionLength = metadataInt(question, "maxLength", 500);
            if (value.length() < minLength) {
                throw new IllegalArgumentException("Поле «" + questionLabel(question) + "» должно содержать минимум " + minLength + " символов");
            }
            if (value.length() > maxQuestionLength) {
                throw new IllegalArgumentException("Поле «" + questionLabel(question) + "» превышает лимит " + maxQuestionLength + " символов");
            }
            validateByType(question, value, answers);
        }
    }

    private void validateByType(PublicFormQuestion question, String value, Map<String, String> answers) {
        String type = Optional.ofNullable(question.type()).orElse("text").toLowerCase(Locale.ROOT);
        if ("checkbox".equals(type)) {
            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value) && !"1".equals(value) && !"0".equals(value)) {
                throw new IllegalArgumentException("Поле «" + questionLabel(question) + "» должно быть булевым значением");
            }
            if (isRequired(question) && !("true".equalsIgnoreCase(value) || "1".equals(value))) {
                throw new IllegalArgumentException("Подтвердите поле: " + questionLabel(question));
            }
            return;
        }
        if ("email".equals(type) && !EMAIL_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Поле «" + questionLabel(question) + "» должно содержать корректный email");
        }
        if ("phone".equals(type) && !PHONE_PATTERN.matcher(value).matches()) {
            throw new IllegalArgumentException("Поле «" + questionLabel(question) + "» должно содержать корректный телефон");
        }
        if ("select".equals(type)) {
            List<String> options = resolveSelectOptions(question, answers);
            boolean allowCustom = metadataBoolean(question, "allowCustom", false);
            boolean validOption = options.stream().anyMatch(value::equalsIgnoreCase);
            boolean rejectValue = LOCATION_FIELD_IDS.contains(question.id())
                    ? !validOption
                    : !options.isEmpty() && !validOption;
            if (!allowCustom && rejectValue) {
                throw new IllegalArgumentException("Поле «" + questionLabel(question) + "» содержит недопустимое значение");
            }
        }
    }

    private void enforceCaptcha(PublicFormConfig config, PublicFormSubmission submission) {
        if (!config.captchaEnabled()) {
            return;
        }
        String mode = runtimeConfigService.readDialogConfigString("public_form_captcha_mode", "shared_secret")
                .trim()
                .toLowerCase(Locale.ROOT);
        if ("turnstile".equals(mode)) {
            verifyTurnstileCaptcha(submission.captchaToken());
            return;
        }
        String expected = runtimeConfigService.readDialogConfigString("public_form_captcha_shared_secret", "");
        String token = submission.captchaToken();
        if (!StringUtils.hasText(expected)) {
            throw new IllegalArgumentException("CAPTCHA включена, но секрет не настроен");
        }
        if (!StringUtils.hasText(token) || !expected.trim().equals(token.trim())) {
            throw new IllegalArgumentException("Проверка CAPTCHA не пройдена");
        }
    }

    private void verifyTurnstileCaptcha(String token) {
        String secret = runtimeConfigService.readDialogConfigString("public_form_turnstile_secret_key", "");
        if (!StringUtils.hasText(secret)) {
            throw new IllegalArgumentException("Turnstile включён, но secret key не настроен");
        }
        if (!StringUtils.hasText(token)) {
            throw new IllegalArgumentException("Проверка CAPTCHA не пройдена");
        }
        String verifyUrl = runtimeConfigService.readDialogConfigString(
                "public_form_turnstile_verify_url",
                "https://challenges.cloudflare.com/turnstile/v0/siteverify"
        );
        int timeoutMs = runtimeConfigService.readDialogConfigInt("public_form_turnstile_timeout_ms", 4000, 500, 15000);
        String payload = "secret=" + urlEncode(secret)
                + "&response=" + urlEncode(token.trim());
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(verifyUrl))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(java.time.Duration.ofMillis(timeoutMs))
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new IllegalArgumentException("CAPTCHA-сервис недоступен");
            }
            JsonNode root = objectMapper.readTree(Optional.ofNullable(response.body()).orElse("{}"));
            if (!root.path("success").asBoolean(false)) {
                throw new IllegalArgumentException("Проверка CAPTCHA не пройдена");
            }
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ex) {
            log.warn("Turnstile verification failed: {}", ex.getMessage());
            throw new IllegalArgumentException("Не удалось проверить CAPTCHA, попробуйте позже");
        }
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(Optional.ofNullable(value).orElse(""), StandardCharsets.UTF_8);
    }

    private List<String> resolveSelectOptions(PublicFormQuestion question, Map<String, String> answers) {
        List<String> flatOptions = metadataStringList(question, "options");
        if (question == null || !LOCATION_FIELD_IDS.contains(question.id())) {
            return flatOptions;
        }
        Map<String, Object> tree = metadataMap(question, "tree");
        if (tree.isEmpty()) {
            return flatOptions;
        }
        return switch (question.id()) {
            case "business" -> flatOptions;
            case "location_type" -> {
                String business = answers.get("business");
                yield toStringList(findNestedValue(tree, business));
            }
            case "city" -> {
                String business = answers.get("business");
                String locationType = answers.get("location_type");
                Map<String, Object> businessNode = toStringObjectMap(findNestedValue(tree, business));
                yield toStringList(findNestedValue(businessNode, locationType));
            }
            case "location_name" -> {
                String business = answers.get("business");
                String locationType = answers.get("location_type");
                String city = answers.get("city");
                Map<String, Object> businessNode = toStringObjectMap(findNestedValue(tree, business));
                Map<String, Object> typeNode = toStringObjectMap(findNestedValue(businessNode, locationType));
                yield toStringList(findNestedValue(typeNode, city));
            }
            default -> flatOptions;
        };
    }

    private Object findNestedValue(Map<String, Object> source, String key) {
        if (source == null || source.isEmpty() || !StringUtils.hasText(key)) {
            return null;
        }
        for (Map.Entry<String, Object> entry : source.entrySet()) {
            if (entry.getKey().equalsIgnoreCase(key.trim())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private Map<String, Object> metadataMap(PublicFormQuestion question, String key) {
        if (question == null || question.metadata() == null) {
            return Map.of();
        }
        return toStringObjectMap(question.metadata().get(key));
    }

    private Map<String, Object> toStringObjectMap(Object rawValue) {
        if (rawValue instanceof Map<?, ?> rawMap) {
            LinkedHashMap<String, Object> result = new LinkedHashMap<>();
            rawMap.forEach((key, value) -> {
                if (key != null) {
                    result.put(String.valueOf(key), value);
                }
            });
            return result;
        }
        return Map.of();
    }

    private List<String> toStringList(Object rawValue) {
        if (rawValue instanceof List<?> list) {
            return list.stream()
                    .map(String::valueOf)
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .toList();
        }
        if (rawValue instanceof Iterable<?> iterable) {
            List<String> result = new java.util.ArrayList<>();
            for (Object item : iterable) {
                String value = item != null ? item.toString().trim() : "";
                if (StringUtils.hasText(value)) {
                    result.add(value);
                }
            }
            return result;
        }
        return List.of();
    }

    private Map<String, String> sanitizeAnswers(Map<String, String> source, boolean stripHtmlTags) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((key, value) -> {
            if (StringUtils.hasText(key) && StringUtils.hasText(value)) {
                String normalizedValue = normalizeFreeText(value, stripHtmlTags);
                if (StringUtils.hasText(normalizedValue)) {
                    result.put(key.trim(), normalizedValue);
                }
            }
        });
        return result;
    }

    private PublicFormSubmission normalizeSubmission(PublicFormSubmission submission, boolean stripHtmlTags) {
        return new PublicFormSubmission(
                normalizeFreeText(submission.message(), stripHtmlTags),
                normalizeFreeText(submission.clientName(), stripHtmlTags),
                normalizeFreeText(submission.clientContact(), stripHtmlTags),
                normalizeFreeText(submission.username(), stripHtmlTags),
                submission.captchaToken(),
                submission.answers(),
                submission.requestId()
        );
    }

    private String normalizeFreeText(String value, boolean stripHtmlTags) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (stripHtmlTags) {
            normalized = HTML_TAG_PATTERN.matcher(normalized).replaceAll("");
        }
        normalized = CONTROL_CHARS_PATTERN.matcher(normalized).replaceAll("");
        normalized = normalized.trim();
        return StringUtils.hasText(normalized) ? normalized : null;
    }

    private String buildCombinedMessage(PublicFormConfig config, Map<String, String> answers, String message) {
        List<String> summary = config.questions().stream()
                .filter(q -> answers.containsKey(q.id()))
                .map(q -> formatSummaryLine(q, answers.get(q.id())))
                .filter(StringUtils::hasText)
                .toList();
        StringBuilder builder = new StringBuilder();
        if (!summary.isEmpty()) {
            builder.append("Ответы формы:\n");
            summary.forEach(line -> builder.append(line).append('\n'));
            if (StringUtils.hasText(message)) {
                builder.append('\n');
            }
        }
        if (StringUtils.hasText(message)) {
            builder.append(message.trim());
        }
        return builder.length() > 0 ? builder.toString() : message;
    }

    private String formatSummaryLine(PublicFormQuestion question, String answer) {
        if (!StringUtils.hasText(answer)) {
            return null;
        }
        return questionLabel(question) + ": " + answer.trim();
    }

    private String resolveClientName(PublicFormSubmission submission, Map<String, String> answers) {
        if (StringUtils.hasText(submission.clientName())) {
            return submission.clientName().trim();
        }
        for (String key : List.of("client_name", "name", "full_name")) {
            if (answers.containsKey(key) && StringUtils.hasText(answers.get(key))) {
                return answers.get(key);
            }
        }
        return "Клиент веб-формы";
    }

    private boolean isRequired(PublicFormQuestion question) {
        return metadataBoolean(question, "required", false);
    }

    private String questionLabel(PublicFormQuestion question) {
        return StringUtils.hasText(question.text()) ? question.text().trim() : "Вопрос";
    }

    private boolean metadataBoolean(PublicFormQuestion question, String key, boolean defaultValue) {
        if (question.metadata() == null) {
            return defaultValue;
        }
        Object raw = question.metadata().get(key);
        if (raw instanceof Boolean bool) {
            return bool;
        }
        if (raw instanceof Number number) {
            return number.intValue() != 0;
        }
        if (raw instanceof String text) {
            return "true".equalsIgnoreCase(text.trim()) || "1".equals(text.trim());
        }
        return defaultValue;
    }

    private int metadataInt(PublicFormQuestion question, String key, int defaultValue) {
        if (question.metadata() == null) {
            return defaultValue;
        }
        Object raw = question.metadata().get(key);
        if (raw instanceof Number number) {
            return number.intValue();
        }
        if (raw instanceof String text) {
            try {
                return Integer.parseInt(text.trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private List<String> metadataStringList(PublicFormQuestion question, String key) {
        if (question.metadata() == null) {
            return List.of();
        }
        Object raw = question.metadata().get(key);
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).map(String::trim).filter(StringUtils::hasText).toList();
        }
        if (raw instanceof String text) {
            return Arrays.stream(text.split(",")).map(String::trim).filter(StringUtils::hasText).toList();
        }
        return List.of();
    }

    public record PreparedSubmission(PublicFormSubmission submission,
                                     Map<String, String> answers,
                                     String combinedMessage,
                                     String clientName) {
    }
}
