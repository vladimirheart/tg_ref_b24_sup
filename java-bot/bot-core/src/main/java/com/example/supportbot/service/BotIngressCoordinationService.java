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
        return tryAcquireOrRenewLease("ingress", platform, channelId, null, properties.getIngressLeaseTtl());
    }

    public boolean tryAcquireOrRenew(String platform, String ingressIdentity) {
        return tryAcquireOrRenewLease("ingress", platform, ingressIdentity, null, properties.getIngressLeaseTtl());
    }

    public boolean tryAcquireOrRenewJob(String platform, Long channelId, String jobName) {
        return tryAcquireOrRenewLease("job", platform, channelId, jobName, properties.getJobLeaseTtl());
    }

    public boolean isCurrentOwner(String platform, Long channelId) {
        String leaseKey = buildLeaseKey("ingress", platform, channelId, null);
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
        releaseLease("ingress", platform, channelId, null);
    }

    public void release(String platform, String ingressIdentity) {
        releaseLease("ingress", platform, ingressIdentity, null);
    }

    public void releaseJob(String platform, Long channelId, String jobName) {
        releaseLease("job", platform, channelId, jobName);
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

    private boolean tryAcquireOrRenewLease(String leaseType,
                                           String platform,
                                           Long channelId,
                                           String leaseName,
                                           Duration configuredTtl) {
        return tryAcquireOrRenewLease(leaseType, platform, normalizeChannelId(channelId), leaseName, configuredTtl);
    }

    private boolean tryAcquireOrRenewLease(String leaseType,
                                           String platform,
                                           String ingressIdentity,
                                           String leaseName,
                                           Duration configuredTtl) {
        String leaseKey = buildLeaseKey(leaseType, platform, ingressIdentity, leaseName);
        if (!properties.isRedisMode()) {
            localOwnership.put(leaseKey, Boolean.TRUE);
            return true;
        }
        StringRedisTemplate redis = requireRedisTemplate();
        Duration ttl = safeDuration(configuredTtl, Duration.ofSeconds(45));
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
            throw new IllegalStateException("Redis coordination is unavailable for " + leaseKey + ".", ex);
        }
    }

    private void releaseLease(String leaseType, String platform, Long channelId, String leaseName) {
        releaseLease(leaseType, platform, normalizeChannelId(channelId), leaseName);
    }

    private void releaseLease(String leaseType, String platform, String ingressIdentity, String leaseName) {
        String leaseKey = buildLeaseKey(leaseType, platform, ingressIdentity, leaseName);
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

    private StringRedisTemplate requireRedisTemplate() {
        if (stringRedisTemplate == null) {
            throw new IllegalStateException("Bot ingress coordination is configured for Redis, but StringRedisTemplate is unavailable.");
        }
        return stringRedisTemplate;
    }

    private String buildLeaseKey(String leaseType, String platform, Long channelId, String leaseName) {
        return buildLeaseKey(leaseType, platform, normalizeChannelId(channelId), leaseName);
    }

    private String buildLeaseKey(String leaseType, String platform, String ingressIdentity, String leaseName) {
        String namespace = StringUtils.hasText(properties.getLeaseNamespace())
            ? properties.getLeaseNamespace().trim()
            : "iguana";
        String normalizedLeaseType = StringUtils.hasText(leaseType) ? leaseType.trim().toLowerCase() : "ingress";
        String normalizedPlatform = StringUtils.hasText(platform) ? platform.trim().toLowerCase() : "unknown";
        String normalizedChannel = StringUtils.hasText(ingressIdentity) ? ingressIdentity.trim() : "0";
        StringBuilder key = new StringBuilder(namespace)
            .append(":bot-lease:")
            .append(normalizedLeaseType)
            .append(":")
            .append(normalizedPlatform)
            .append(":channel:")
            .append(normalizedChannel);
        if (StringUtils.hasText(leaseName)) {
            key.append(":name:").append(leaseName.trim().toLowerCase());
        }
        return key.toString();
    }

    private String normalizeChannelId(Long channelId) {
        return channelId == null ? "0" : Long.toString(channelId);
    }

    private Duration safeDuration(Duration value, Duration fallback) {
        if (value == null || value.isZero() || value.isNegative()) {
            return fallback;
        }
        return value;
    }
}
