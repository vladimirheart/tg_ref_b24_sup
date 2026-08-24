package com.example.panel.service;

import com.example.panel.config.RuntimeCoordinationProperties;
import java.time.Instant;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class RuntimeCoordinationService {

    private static final Logger log = LoggerFactory.getLogger(RuntimeCoordinationService.class);
    private static final DefaultRedisScript<Long> RELEASE_LEASE_SCRIPT = new DefaultRedisScript<>(
            """
            if redis.call('get', KEYS[1]) == ARGV[1] then
                return redis.call('del', KEYS[1])
            end
            return 0
            """,
            Long.class
    );

    private final RuntimeCoordinationProperties properties;
    private final StringRedisTemplate stringRedisTemplate;
    private final ConcurrentHashMap<String, AtomicLong> localCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> localCooldowns = new ConcurrentHashMap<>();

    public RuntimeCoordinationService(RuntimeCoordinationProperties properties,
                                      StringRedisTemplate stringRedisTemplate) {
        this.properties = properties;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void verifyReadyForPostgresql() {
        if (!properties.isRequiredForPostgresql()) {
            return;
        }
        verifyAvailable();
    }

    public void verifyAvailable() {
        if (!properties.isRedisMode()) {
            throw new IllegalStateException("Redis coordination requires app.coordination.mode=redis.");
        }
        try {
            String result = stringRedisTemplate.execute((RedisCallback<String>) connection -> connection.ping());
            if (!StringUtils.hasText(result)) {
                throw new IllegalStateException("Redis ping returned empty response.");
            }
        } catch (DataAccessException ex) {
            throw new IllegalStateException("Redis coordination backend is unavailable.", ex);
        }
    }

    public long nextCounterValue(String counterName) {
        String counterKey = buildCounterKey(counterName);
        if (!properties.isRedisMode()) {
            return nextLocalCounterValue(counterKey);
        }
        try {
            Long value = stringRedisTemplate.opsForValue().increment(counterKey);
            if (value == null) {
                return nextLocalCounterValue(counterKey);
            }
            return Math.max(0L, value - 1L);
        } catch (RuntimeException ex) {
            log.warn("Falling back to local counter {} because Redis coordination is unavailable: {}",
                counterName, ex.getMessage());
            return nextLocalCounterValue(counterKey);
        }
    }

    public boolean tryAcquireCooldown(String cooldownName, Duration ttl) {
        String cooldownKey = buildCooldownKey(cooldownName);
        Duration safeTtl = ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofSeconds(30) : ttl;
        if (!properties.isRedisMode()) {
            return tryAcquireLocalCooldown(cooldownKey, safeTtl);
        }
        try {
            Boolean acquired = stringRedisTemplate.opsForValue()
                .setIfAbsent(cooldownKey, Long.toString(System.currentTimeMillis()), safeTtl);
            return Boolean.TRUE.equals(acquired);
        } catch (RuntimeException ex) {
            log.warn("Falling back to local cooldown {} because Redis coordination is unavailable: {}",
                cooldownName, ex.getMessage());
            return tryAcquireLocalCooldown(cooldownKey, safeTtl);
        }
    }

    public boolean isCooldownActive(String cooldownName) {
        String cooldownKey = buildCooldownKey(cooldownName);
        if (!properties.isRedisMode()) {
            return isLocalCooldownActive(cooldownKey, Instant.now());
        }
        try {
            return Boolean.TRUE.equals(stringRedisTemplate.hasKey(cooldownKey));
        } catch (RuntimeException ex) {
            log.warn("Falling back to local cooldown lookup {} because Redis coordination is unavailable: {}",
                cooldownName, ex.getMessage());
            return isLocalCooldownActive(cooldownKey, Instant.now());
        }
    }

    public void refreshCooldown(String cooldownName, Duration ttl) {
        String cooldownKey = buildCooldownKey(cooldownName);
        Duration safeTtl = ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofSeconds(30) : ttl;
        if (!properties.isRedisMode()) {
            localCooldowns.put(cooldownKey, Instant.now().plus(safeTtl));
            return;
        }
        try {
            stringRedisTemplate.opsForValue().set(cooldownKey, Long.toString(System.currentTimeMillis()), safeTtl);
        } catch (RuntimeException ex) {
            log.warn("Falling back to local cooldown refresh {} because Redis coordination is unavailable: {}",
                cooldownName, ex.getMessage());
            localCooldowns.put(cooldownKey, Instant.now().plus(safeTtl));
        }
    }

    public long cooldownRemainingSeconds(String cooldownName) {
        String cooldownKey = buildCooldownKey(cooldownName);
        if (!properties.isRedisMode()) {
            return localCooldownRemainingSeconds(cooldownKey, Instant.now());
        }
        try {
            Long ttlSeconds = stringRedisTemplate.getExpire(cooldownKey, TimeUnit.SECONDS);
            return ttlSeconds == null || ttlSeconds <= 0L ? 0L : ttlSeconds;
        } catch (RuntimeException ex) {
            log.warn("Falling back to local cooldown TTL {} because Redis coordination is unavailable: {}",
                cooldownName, ex.getMessage());
            return localCooldownRemainingSeconds(cooldownKey, Instant.now());
        }
    }

    public void clearCooldown(String cooldownName) {
        String cooldownKey = buildCooldownKey(cooldownName);
        if (!properties.isRedisMode()) {
            localCooldowns.remove(cooldownKey);
            return;
        }
        try {
            stringRedisTemplate.delete(cooldownKey);
        } catch (RuntimeException ex) {
            log.warn("Falling back to local cooldown clear {} because Redis coordination is unavailable: {}",
                cooldownName, ex.getMessage());
            localCooldowns.remove(cooldownKey);
        }
    }

    public void runWithLease(String leaseName, Duration ttl, Runnable action) {
        if (action == null) {
            return;
        }
        if (!properties.isRedisMode()) {
            action.run();
            return;
        }
        String leaseKey = buildLeaseKey(leaseName);
        String token = UUID.randomUUID().toString();
        Duration safeTtl = ttl == null || ttl.isNegative() || ttl.isZero() ? Duration.ofSeconds(30) : ttl;
        boolean acquired = false;
        try {
            Boolean value = stringRedisTemplate.opsForValue().setIfAbsent(leaseKey, token, safeTtl);
            acquired = Boolean.TRUE.equals(value);
        } catch (RuntimeException ex) {
            log.warn("Skipping leased action {} because Redis coordination is unavailable: {}", leaseName, ex.getMessage());
            return;
        }
        if (!acquired) {
            return;
        }
        try {
            action.run();
        } finally {
            try {
                stringRedisTemplate.execute(RELEASE_LEASE_SCRIPT, java.util.List.of(leaseKey), token);
            } catch (RuntimeException ex) {
                log.debug("Unable to release Redis lease {}: {}", leaseKey, ex.getMessage());
            }
        }
    }

    private long nextLocalCounterValue(String counterKey) {
        return localCounters.computeIfAbsent(counterKey, key -> new AtomicLong(0L)).getAndIncrement();
    }

    private boolean tryAcquireLocalCooldown(String cooldownKey, Duration ttl) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(ttl);
        AtomicBoolean acquired = new AtomicBoolean(false);
        localCooldowns.compute(cooldownKey, (key, existing) -> {
            if (existing == null || !existing.isAfter(now)) {
                acquired.set(true);
                return expiresAt;
            }
            return existing;
        });
        return acquired.get();
    }

    private boolean isLocalCooldownActive(String cooldownKey, Instant now) {
        Instant expiresAt = localCooldowns.get(cooldownKey);
        if (expiresAt == null) {
            return false;
        }
        if (!expiresAt.isAfter(now)) {
            localCooldowns.remove(cooldownKey, expiresAt);
            return false;
        }
        return true;
    }

    private long localCooldownRemainingSeconds(String cooldownKey, Instant now) {
        Instant expiresAt = localCooldowns.get(cooldownKey);
        if (expiresAt == null) {
            return 0L;
        }
        if (!expiresAt.isAfter(now)) {
            localCooldowns.remove(cooldownKey, expiresAt);
            return 0L;
        }
        return Math.max(1L, Duration.between(now, expiresAt).getSeconds());
    }

    private String buildLeaseKey(String leaseName) {
        String namespace = StringUtils.hasText(properties.getLeaseNamespace())
                ? properties.getLeaseNamespace().trim()
                : "iguana";
        String suffix = StringUtils.hasText(leaseName) ? leaseName.trim() : "unnamed";
        return namespace + ":lease:" + suffix;
    }

    private String buildCounterKey(String counterName) {
        String namespace = StringUtils.hasText(properties.getLeaseNamespace())
            ? properties.getLeaseNamespace().trim()
            : "iguana";
        String suffix = StringUtils.hasText(counterName) ? counterName.trim() : "unnamed";
        return namespace + ":counter:" + suffix;
    }

    private String buildCooldownKey(String cooldownName) {
        String namespace = StringUtils.hasText(properties.getLeaseNamespace())
            ? properties.getLeaseNamespace().trim()
            : "iguana";
        String suffix = StringUtils.hasText(cooldownName) ? cooldownName.trim() : "unnamed";
        return namespace + ":cooldown:" + suffix;
    }
}
