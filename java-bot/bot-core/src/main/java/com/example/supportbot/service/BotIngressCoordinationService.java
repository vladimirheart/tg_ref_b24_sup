package com.example.supportbot.service;

import com.example.supportbot.config.BotIngressCoordinationProperties;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BotIngressCoordinationService {

    private static final Logger log = LoggerFactory.getLogger(BotIngressCoordinationService.class);

    private static final DefaultRedisScript<Long> RENEW_LEASE_SCRIPT = new DefaultRedisScript<>(
        """
        if redis.call('get', KEYS[1]) == ARGV[1] then
            return redis.call('pexpire', KEYS[1], ARGV[2])
        end
        return 0
        """,
        Long.class
    );

    private static final DefaultRedisScript<Long> RELEASE_LEASE_SCRIPT = new DefaultRedisScript<>(
        """
        if redis.call('get', KEYS[1]) == ARGV[1] then
            return redis.call('del', KEYS[1])
        end
        return 0
        """,
        Long.class
    );

    private final BotIngressCoordinationProperties properties;
    private final StringRedisTemplate stringRedisTemplate;
    private final String instanceToken = UUID.randomUUID().toString();
    private final ConcurrentHashMap<String, Boolean> localOwnership = new ConcurrentHashMap<>();

    public BotIngressCoordinationService(BotIngressCoordinationProperties properties,
                                         ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider) {
        this.properties = properties;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public boolean tryAcquireOrRenew(String platform, Long channelId) {
        String leaseKey = buildLeaseKey(platform, channelId);
        if (!properties.isRedisMode()) {
            localOwnership.put(leaseKey, Boolean.TRUE);
            return true;
        }
        StringRedisTemplate redis = requireRedisTemplate();
        Duration ttl = safeDuration(properties.getIngressLeaseTtl(), Duration.ofSeconds(45));
        try {
            Long renewed = redis.execute(
                RENEW_LEASE_SCRIPT,
                List.of(leaseKey),
                instanceToken,
                Long.toString(ttl.toMillis())
            );
            if (renewed != null && renewed > 0L) {
                localOwnership.put(leaseKey, Boolean.TRUE);
                return true;
            }
            Boolean acquired = redis.opsForValue().setIfAbsent(leaseKey, instanceToken, ttl);
            boolean owner = Boolean.TRUE.equals(acquired);
            localOwnership.put(leaseKey, owner);
            return owner;
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Redis ingress coordination is unavailable for " + leaseKey + ".", ex);
        }
    }

    public boolean isCurrentOwner(String platform, Long channelId) {
        String leaseKey = buildLeaseKey(platform, channelId);
        if (!properties.isRedisMode()) {
            return true;
        }
        if (!Boolean.TRUE.equals(localOwnership.get(leaseKey))) {
            return false;
        }
        StringRedisTemplate redis = requireRedisTemplate();
        try {
            String value = redis.opsForValue().get(leaseKey);
            boolean owner = instanceToken.equals(value);
            localOwnership.put(leaseKey, owner);
            return owner;
        } catch (RuntimeException ex) {
            throw new IllegalStateException("Unable to verify Redis ingress lease for " + leaseKey + ".", ex);
        }
    }

    public void release(String platform, Long channelId) {
        String leaseKey = buildLeaseKey(platform, channelId);
        localOwnership.remove(leaseKey);
        if (!properties.isRedisMode()) {
            return;
        }
        StringRedisTemplate redis = requireRedisTemplate();
        try {
            redis.execute(RELEASE_LEASE_SCRIPT, List.of(leaseKey), instanceToken);
        } catch (RuntimeException ex) {
            log.debug("Unable to release ingress lease {}: {}", leaseKey, ex.getMessage());
        }
    }

    public Duration renewInterval() {
        return safeDuration(properties.getIngressLeaseRenewInterval(), Duration.ofSeconds(15));
    }

    public Duration followerBackoff() {
        return safeDuration(properties.getIngressFollowerBackoff(), Duration.ofSeconds(5));
    }

    public boolean isRedisMode() {
        return properties.isRedisMode();
    }

    private StringRedisTemplate requireRedisTemplate() {
        if (stringRedisTemplate == null) {
            throw new IllegalStateException("Bot ingress coordination is configured for Redis, but StringRedisTemplate is unavailable.");
        }
        return stringRedisTemplate;
    }

    private String buildLeaseKey(String platform, Long channelId) {
        String namespace = StringUtils.hasText(properties.getLeaseNamespace())
            ? properties.getLeaseNamespace().trim()
            : "iguana";
        String normalizedPlatform = StringUtils.hasText(platform) ? platform.trim().toLowerCase() : "unknown";
        String normalizedChannel = channelId == null ? "0" : Long.toString(channelId);
        return namespace + ":bot-ingress:" + normalizedPlatform + ":channel:" + normalizedChannel;
    }

    private Duration safeDuration(Duration value, Duration fallback) {
        if (value == null || value.isZero() || value.isNegative()) {
            return fallback;
        }
        return value;
    }
}
