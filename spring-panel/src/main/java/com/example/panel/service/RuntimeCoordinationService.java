package com.example.panel.service;

import com.example.panel.config.RuntimeCoordinationProperties;
import java.time.Duration;
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

    public RuntimeCoordinationService(RuntimeCoordinationProperties properties,
                                      StringRedisTemplate stringRedisTemplate) {
        this.properties = properties;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void verifyReadyForPostgresql() {
        if (!properties.isRequiredForPostgresql()) {
            return;
        }
        if (!properties.isRedisMode()) {
            throw new IllegalStateException("PostgreSQL runtime requires app.coordination.mode=redis.");
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

    private String buildLeaseKey(String leaseName) {
        String namespace = StringUtils.hasText(properties.getLeaseNamespace())
                ? properties.getLeaseNamespace().trim()
                : "iguana";
        String suffix = StringUtils.hasText(leaseName) ? leaseName.trim() : "unnamed";
        return namespace + ":lease:" + suffix;
    }
}
