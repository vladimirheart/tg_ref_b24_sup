package com.example.supportbot.service;

import com.example.supportbot.config.BotIngressCoordinationProperties;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;

@Service
public class BotWebhookDeliveryGuardService {

    private static final DefaultRedisScript<Long> CLAIM_SCRIPT = new DefaultRedisScript<>(
        """
        local current = redis.call('get', KEYS[1])
        if not current then
            redis.call('set', KEYS[1], 'inflight:' .. ARGV[1], 'PX', ARGV[2])
            return 1
        end
        if string.sub(current, 1, 10) == 'processed:' then
            return 2
        end
        return 0
        """,
        Long.class
    );

    private static final DefaultRedisScript<Long> MARK_PROCESSED_SCRIPT = new DefaultRedisScript<>(
        """
        if redis.call('get', KEYS[1]) == 'inflight:' .. ARGV[1] then
            redis.call('set', KEYS[1], 'processed:' .. ARGV[1], 'PX', ARGV[2])
            return 1
        end
        return 0
        """,
        Long.class
    );

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
        """
        if redis.call('get', KEYS[1]) == 'inflight:' .. ARGV[1] then
            return redis.call('del', KEYS[1])
        end
        return 0
        """,
        Long.class
    );

    private final BotIngressCoordinationProperties properties;
    private final StringRedisTemplate stringRedisTemplate;
    private final ConcurrentHashMap<String, String> localDeliveries = new ConcurrentHashMap<>();

    public BotWebhookDeliveryGuardService(BotIngressCoordinationProperties properties,
                                          ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider) {
        this.properties = properties;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public DeliveryClaim tryClaim(String platform, Long channelId, String deliveryKey) {
        String storageKey = buildStorageKey(platform, channelId, deliveryKey);
        String token = UUID.randomUUID().toString();
        if (!properties.isRedisMode()) {
            String existing = localDeliveries.putIfAbsent(storageKey, inflightValue(token));
            if (existing == null) {
                return new DeliveryClaim(storageKey, token, ClaimStatus.ACQUIRED);
            }
            if (existing.startsWith("processed:")) {
                return new DeliveryClaim(storageKey, token, ClaimStatus.ALREADY_PROCESSED);
            }
            return new DeliveryClaim(storageKey, token, ClaimStatus.IN_FLIGHT);
        }
        Duration inflightTtl = safeDuration(properties.getWebhookDeliveryInflightTtl(), Duration.ofMinutes(2));
        Long claimed = requireRedisTemplate().execute(
            CLAIM_SCRIPT,
            List.of(storageKey),
            token,
            Long.toString(inflightTtl.toMillis())
        );
        if (claimed != null && claimed == 1L) {
            return new DeliveryClaim(storageKey, token, ClaimStatus.ACQUIRED);
        }
        if (claimed != null && claimed == 2L) {
            return new DeliveryClaim(storageKey, token, ClaimStatus.ALREADY_PROCESSED);
        }
        return new DeliveryClaim(storageKey, token, ClaimStatus.IN_FLIGHT);
    }

    public void markProcessed(DeliveryClaim claim) {
        if (claim == null || !claim.acquired()) {
            return;
        }
        if (!properties.isRedisMode()) {
            localDeliveries.computeIfPresent(claim.storageKey(), (ignored, current) ->
                current.equals(inflightValue(claim.token())) ? processedValue(claim.token()) : current);
            return;
        }
        Duration processedTtl = safeDuration(properties.getWebhookDeliveryProcessedTtl(), Duration.ofHours(6));
        requireRedisTemplate().execute(
            MARK_PROCESSED_SCRIPT,
            List.of(claim.storageKey()),
            claim.token(),
            Long.toString(processedTtl.toMillis())
        );
    }

    public void release(DeliveryClaim claim) {
        if (claim == null || !claim.acquired()) {
            return;
        }
        if (!properties.isRedisMode()) {
            localDeliveries.remove(claim.storageKey(), inflightValue(claim.token()));
            return;
        }
        requireRedisTemplate().execute(RELEASE_SCRIPT, List.of(claim.storageKey()), claim.token());
    }

    public record DeliveryClaim(String storageKey, String token, ClaimStatus status) {
        public boolean acquired() {
            return status == ClaimStatus.ACQUIRED;
        }

        public boolean alreadyProcessed() {
            return status == ClaimStatus.ALREADY_PROCESSED;
        }

        public boolean inFlight() {
            return status == ClaimStatus.IN_FLIGHT;
        }
    }

    public enum ClaimStatus {
        ACQUIRED,
        ALREADY_PROCESSED,
        IN_FLIGHT
    }

    private String buildStorageKey(String platform, Long channelId, String deliveryKey) {
        String namespace = StringUtils.hasText(properties.getLeaseNamespace())
            ? properties.getLeaseNamespace().trim()
            : "iguana";
        String normalizedPlatform = StringUtils.hasText(platform) ? platform.trim().toLowerCase() : "unknown";
        String normalizedChannel = channelId == null ? "0" : Long.toString(channelId);
        String normalizedDeliveryKey = StringUtils.hasText(deliveryKey) ? deliveryKey.trim() : "missing";
        String digest = DigestUtils.md5DigestAsHex(normalizedDeliveryKey.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        return namespace + ":bot-webhook-delivery:" + normalizedPlatform + ":channel:" + normalizedChannel + ":key:" + digest;
    }

    private String inflightValue(String token) {
        return "inflight:" + token;
    }

    private String processedValue(String token) {
        return "processed:" + token;
    }

    private StringRedisTemplate requireRedisTemplate() {
        if (stringRedisTemplate == null) {
            throw new IllegalStateException("Webhook delivery guard is configured for Redis, but StringRedisTemplate is unavailable.");
        }
        return stringRedisTemplate;
    }

    private Duration safeDuration(Duration value, Duration fallback) {
        if (value == null || value.isZero() || value.isNegative()) {
            return fallback;
        }
        return value;
    }
}
