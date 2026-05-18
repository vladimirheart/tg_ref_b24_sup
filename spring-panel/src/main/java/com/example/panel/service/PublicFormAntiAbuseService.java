package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.model.publicform.PublicFormSessionDto;
import com.example.panel.model.publicform.PublicFormSubmission;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class PublicFormAntiAbuseService {

    private final PublicFormRuntimeConfigService runtimeConfigService;
    private final ObjectMapper objectMapper;
    private final Map<String, Deque<Long>> rateLimitBuckets = new ConcurrentHashMap<>();
    private final Map<String, IdempotencyEntry> idempotencyCache = new ConcurrentHashMap<>();

    public PublicFormAntiAbuseService(PublicFormRuntimeConfigService runtimeConfigService,
                                      ObjectMapper objectMapper) {
        this.runtimeConfigService = runtimeConfigService;
        this.objectMapper = objectMapper;
    }

    public SubmissionFingerprint prepareSubmissionFingerprint(PublicFormSubmission submission,
                                                              Map<String, String> answers) {
        String requestId = normalizeRequestId(submission != null ? submission.requestId() : null);
        String payloadHash = buildPayloadHash(submission, answers);
        return new SubmissionFingerprint(requestId, payloadHash);
    }

    public Optional<PublicFormSessionDto> findIdempotentSession(Channel channel,
                                                                String requesterKey,
                                                                SubmissionFingerprint fingerprint) {
        if (fingerprint == null || !StringUtils.hasText(fingerprint.requestId())) {
            return Optional.empty();
        }
        purgeExpiredIdempotencyEntries();
        IdempotencyEntry entry = idempotencyCache.get(idempotencyCacheKey(channel, requesterKey, fingerprint.requestId()));
        if (entry == null) {
            return Optional.empty();
        }
        if (!entry.payloadHash().equals(fingerprint.payloadHash())) {
            throw new IllegalArgumentException("Запрос с таким requestId уже был отправлен с другим payload");
        }
        return Optional.of(entry.session());
    }

    public void cacheIdempotentSession(Channel channel,
                                       String requesterKey,
                                       SubmissionFingerprint fingerprint,
                                       PublicFormSessionDto session) {
        if (fingerprint == null || !StringUtils.hasText(fingerprint.requestId())) {
            return;
        }
        purgeExpiredIdempotencyEntries();
        idempotencyCache.put(
                idempotencyCacheKey(channel, requesterKey, fingerprint.requestId()),
                new IdempotencyEntry(fingerprint.payloadHash(), session, System.currentTimeMillis())
        );
    }

    public void enforceRateLimit(Channel channel, String requesterKey) {
        RateLimitPolicy policy = resolveRateLimitPolicy(channel);
        if (!policy.enabled()) {
            return;
        }
        String bucketKey = (channel == null || channel.getId() == null ? "unknown" : channel.getId())
                + ":" + (StringUtils.hasText(requesterKey) ? requesterKey : "anon");
        long now = System.currentTimeMillis();
        long threshold = now - (policy.windowSeconds() * 1000L);
        Deque<Long> bucket = rateLimitBuckets.computeIfAbsent(bucketKey, key -> new ArrayDeque<>());
        synchronized (bucket) {
            while (!bucket.isEmpty() && bucket.peekFirst() < threshold) {
                bucket.removeFirst();
            }
            if (bucket.size() >= policy.maxRequests()) {
                throw new IllegalArgumentException("Слишком много запросов. Попробуйте чуть позже.");
            }
            bucket.addLast(now);
        }
    }

    public String buildRequesterKey(String requesterIp, String fingerprint) {
        String ipPart = StringUtils.hasText(requesterIp) ? requesterIp.trim() : "anon";
        if (!runtimeConfigService.readDialogConfigBoolean("public_form_rate_limit_use_fingerprint", true)) {
            return ipPart;
        }
        if (!StringUtils.hasText(fingerprint)) {
            return ipPart;
        }
        String normalizedFingerprint = fingerprint.trim();
        if (normalizedFingerprint.length() > 256) {
            normalizedFingerprint = normalizedFingerprint.substring(0, 256);
        }
        return ipPart + "|fp:" + hashValue(normalizedFingerprint);
    }

    public String summarizeRequester(String requesterKey) {
        if (!StringUtils.hasText(requesterKey)) {
            return "unknown";
        }
        String normalized = requesterKey.trim();
        if (normalized.length() <= 12) {
            return hashValue(normalized).substring(0, 12);
        }
        return normalized.substring(0, 6) + "…" + hashValue(normalized).substring(0, 6);
    }

    private void purgeExpiredIdempotencyEntries() {
        long now = System.currentTimeMillis();
        int ttlSeconds = runtimeConfigService.readDialogConfigInt("public_form_idempotency_ttl_seconds", 300, 30, 3600);
        long ttlMs = ttlSeconds * 1000L;
        idempotencyCache.entrySet().removeIf(entry -> (now - entry.getValue().createdAtMs()) > ttlMs);
    }

    private String normalizeRequestId(String requestId) {
        if (!StringUtils.hasText(requestId)) {
            return null;
        }
        String normalized = requestId.trim();
        if (normalized.length() > 128) {
            throw new IllegalArgumentException("requestId слишком длинный");
        }
        return normalized;
    }

    private String buildPayloadHash(PublicFormSubmission submission, Map<String, String> answers) {
        StringBuilder builder = new StringBuilder()
                .append(Optional.ofNullable(submission).map(PublicFormSubmission::message).orElse("").trim())
                .append('|')
                .append(Optional.ofNullable(submission).map(PublicFormSubmission::clientName).orElse("").trim())
                .append('|')
                .append(Optional.ofNullable(submission).map(PublicFormSubmission::clientContact).orElse("").trim());
        if (answers != null && !answers.isEmpty()) {
            for (Map.Entry<String, String> entry : answers.entrySet().stream().sorted(Map.Entry.comparingByKey()).toList()) {
                builder.append('|').append(entry.getKey()).append('=')
                        .append(Optional.ofNullable(entry.getValue()).orElse(""));
            }
        }
        return hashValue(builder.toString());
    }

    private String hashValue(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(Optional.ofNullable(payload).orElse("").getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) {
            throw new IllegalStateException("Не удалось подготовить хеш запроса", ex);
        }
    }

    private String idempotencyCacheKey(Channel channel, String requesterKey, String requestId) {
        String requester = StringUtils.hasText(requesterKey) ? requesterKey.trim() : "unknown";
        return (channel == null ? null : channel.getId()) + "|" + requester + "|" + requestId;
    }

    private RateLimitPolicy resolveRateLimitPolicy(Channel channel) {
        boolean defaultEnabled = runtimeConfigService.readDialogConfigBoolean("public_form_rate_limit_enabled", true);
        int defaultWindowSeconds = runtimeConfigService.readDialogConfigInt("public_form_rate_limit_window_seconds", 60, 10, 3600);
        int defaultMaxRequests = runtimeConfigService.readDialogConfigInt("public_form_rate_limit_max_requests", 5, 1, 500);
        if (channel == null || !StringUtils.hasText(channel.getQuestionsCfg())) {
            return new RateLimitPolicy(defaultEnabled, defaultWindowSeconds, defaultMaxRequests);
        }
        try {
            JsonNode root = objectMapper.readTree(channel.getQuestionsCfg());
            if (!root.isObject()) {
                return new RateLimitPolicy(defaultEnabled, defaultWindowSeconds, defaultMaxRequests);
            }
            boolean enabled = root.has("rateLimitEnabled")
                    ? root.path("rateLimitEnabled").asBoolean(defaultEnabled)
                    : defaultEnabled;
            int windowSeconds = root.has("rateLimitWindowSeconds")
                    ? clamp(root.path("rateLimitWindowSeconds").asInt(defaultWindowSeconds), 10, 3600)
                    : defaultWindowSeconds;
            int maxRequests = root.has("rateLimitMaxRequests")
                    ? clamp(root.path("rateLimitMaxRequests").asInt(defaultMaxRequests), 1, 500)
                    : defaultMaxRequests;
            return new RateLimitPolicy(enabled, windowSeconds, maxRequests);
        } catch (Exception ignored) {
            return new RateLimitPolicy(defaultEnabled, defaultWindowSeconds, defaultMaxRequests);
        }
    }

    private int clamp(int value, int minValue, int maxValue) {
        return Math.max(minValue, Math.min(maxValue, value));
    }

    public record SubmissionFingerprint(String requestId, String payloadHash) {
    }

    private record IdempotencyEntry(String payloadHash, PublicFormSessionDto session, long createdAtMs) {
    }

    private record RateLimitPolicy(boolean enabled, int windowSeconds, int maxRequests) {
    }
}
