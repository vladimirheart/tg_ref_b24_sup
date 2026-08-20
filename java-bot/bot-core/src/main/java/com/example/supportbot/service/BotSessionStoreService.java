package com.example.supportbot.service;

import com.example.supportbot.config.BotIngressCoordinationProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class BotSessionStoreService {

    private static final DefaultRedisScript<Long> DELETE_IF_MATCHES_SCRIPT = new DefaultRedisScript<>(
        """
        if redis.call('get', KEYS[1]) == ARGV[1] then
            redis.call('del', KEYS[1])
            redis.call('srem', KEYS[2], ARGV[2])
            return 1
        end
        return 0
        """,
        Long.class
    );

    private static final DefaultRedisScript<Long> SAVE_IF_MATCHES_SCRIPT = new DefaultRedisScript<>(
        """
        local current = redis.call('get', KEYS[1])
        if ARGV[1] == '1' then
            if current then
                return 0
            end
        else
            if current ~= ARGV[2] then
                return 0
            end
        end
        redis.call('set', KEYS[1], ARGV[3], 'PX', ARGV[4])
        redis.call('sadd', KEYS[2], ARGV[5])
        redis.call('pexpire', KEYS[2], ARGV[4])
        return 1
        """,
        Long.class
    );

    private final BotIngressCoordinationProperties properties;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ConcurrentHashMap<String, String> localSessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> localIndexes = new ConcurrentHashMap<>();

    public BotSessionStoreService(BotIngressCoordinationProperties properties,
                                  ObjectMapper objectMapper,
                                  ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public <T> Optional<StoredBotSession<T>> load(String platform, Long channelId, Long userId, Class<T> payloadType) {
        if (userId == null) {
            return Optional.empty();
        }
        String sessionKey = buildSessionKey(platform, channelId, userId);
        String rawPayload = readRaw(sessionKey);
        if (!StringUtils.hasText(rawPayload)) {
            removeIndexEntry(platform, channelId, userId);
            return Optional.empty();
        }
        return Optional.of(new StoredBotSession<>(userId, rawPayload, deserialize(rawPayload, payloadType)));
    }

    public <T> List<StoredBotSession<T>> loadAll(String platform, Long channelId, Class<T> payloadType) {
        List<StoredBotSession<T>> sessions = new ArrayList<>();
        for (String userIdValue : indexedUserIds(platform, channelId)) {
            Long userId = parseUserId(userIdValue);
            if (userId == null) {
                removeIndexEntry(platform, channelId, userIdValue);
                continue;
            }
            load(platform, channelId, userId, payloadType).ifPresent(sessions::add);
        }
        return sessions;
    }

    public void save(String platform, Long channelId, Long userId, Object payload) {
        if (userId == null || payload == null) {
            return;
        }
        String sessionKey = buildSessionKey(platform, channelId, userId);
        String indexKey = buildIndexKey(platform, channelId);
        String userIdValue = Long.toString(userId);
        String rawPayload = serialize(payload);
        if (!properties.isRedisMode()) {
            localSessions.put(sessionKey, rawPayload);
            localIndexes.computeIfAbsent(indexKey, ignored -> ConcurrentHashMap.newKeySet()).add(userIdValue);
            return;
        }
        StringRedisTemplate redis = requireRedisTemplate();
        Duration ttl = sessionTtl();
        redis.opsForValue().set(sessionKey, rawPayload, ttl);
        redis.opsForSet().add(indexKey, userIdValue);
        redis.expire(indexKey, ttl);
    }

    public Optional<String> saveIfUnchanged(String platform,
                                            Long channelId,
                                            Long userId,
                                            String expectedRawPayload,
                                            Object payload) {
        if (userId == null || payload == null) {
            return Optional.empty();
        }
        String sessionKey = buildSessionKey(platform, channelId, userId);
        String indexKey = buildIndexKey(platform, channelId);
        String userIdValue = Long.toString(userId);
        String rawPayload = serialize(payload);
        if (!properties.isRedisMode()) {
            boolean saved;
            if (expectedRawPayload == null) {
                saved = localSessions.putIfAbsent(sessionKey, rawPayload) == null;
            } else {
                saved = localSessions.replace(sessionKey, expectedRawPayload, rawPayload);
            }
            if (!saved) {
                return Optional.empty();
            }
            localIndexes.computeIfAbsent(indexKey, ignored -> ConcurrentHashMap.newKeySet()).add(userIdValue);
            return Optional.of(rawPayload);
        }
        Duration ttl = sessionTtl();
        Long updated = requireRedisTemplate().execute(
            SAVE_IF_MATCHES_SCRIPT,
            List.of(sessionKey, indexKey),
            expectedRawPayload == null ? "1" : "0",
            expectedRawPayload == null ? "" : expectedRawPayload,
            rawPayload,
            Long.toString(ttl.toMillis()),
            userIdValue
        );
        if (updated != null && updated > 0L) {
            return Optional.of(rawPayload);
        }
        return Optional.empty();
    }

    public void delete(String platform, Long channelId, Long userId) {
        if (userId == null) {
            return;
        }
        String sessionKey = buildSessionKey(platform, channelId, userId);
        removeIndexEntry(platform, channelId, userId);
        if (!properties.isRedisMode()) {
            localSessions.remove(sessionKey);
            return;
        }
        requireRedisTemplate().delete(sessionKey);
    }

    public boolean deleteIfUnchanged(String platform, Long channelId, Long userId, String expectedRawPayload) {
        if (userId == null || !StringUtils.hasText(expectedRawPayload)) {
            return false;
        }
        String sessionKey = buildSessionKey(platform, channelId, userId);
        String indexKey = buildIndexKey(platform, channelId);
        String userIdValue = Long.toString(userId);
        if (!properties.isRedisMode()) {
            boolean removed = localSessions.remove(sessionKey, expectedRawPayload);
            if (removed) {
                removeIndexEntry(platform, channelId, userId);
            }
            return removed;
        }
        Long deleted = requireRedisTemplate().execute(
            DELETE_IF_MATCHES_SCRIPT,
            List.of(sessionKey, indexKey),
            expectedRawPayload,
            userIdValue
        );
        return deleted != null && deleted > 0L;
    }

    public record StoredBotSession<T>(Long userId, String rawPayload, T payload) {
    }

    private Set<String> indexedUserIds(String platform, Long channelId) {
        String indexKey = buildIndexKey(platform, channelId);
        if (!properties.isRedisMode()) {
            return localIndexes.getOrDefault(indexKey, Set.of());
        }
        Set<String> members = requireRedisTemplate().opsForSet().members(indexKey);
        return members != null ? members : Set.of();
    }

    private void removeIndexEntry(String platform, Long channelId, Long userId) {
        if (userId != null) {
            removeIndexEntry(platform, channelId, Long.toString(userId));
        }
    }

    private void removeIndexEntry(String platform, Long channelId, String userIdValue) {
        if (!StringUtils.hasText(userIdValue)) {
            return;
        }
        String indexKey = buildIndexKey(platform, channelId);
        if (!properties.isRedisMode()) {
            localIndexes.computeIfPresent(indexKey, (ignored, values) -> {
                values.remove(userIdValue);
                return values.isEmpty() ? null : values;
            });
            return;
        }
        requireRedisTemplate().opsForSet().remove(indexKey, userIdValue);
    }

    private String readRaw(String sessionKey) {
        if (!properties.isRedisMode()) {
            return localSessions.get(sessionKey);
        }
        return requireRedisTemplate().opsForValue().get(sessionKey);
    }

    private Duration sessionTtl() {
        Duration ttl = properties.getBotSessionTtl();
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            return Duration.ofHours(24);
        }
        return ttl;
    }

    private String buildSessionKey(String platform, Long channelId, Long userId) {
        return keyPrefix() + ":bot-session:" + normalizedPlatform(platform) + ":channel:" + normalizedChannel(channelId)
            + ":user:" + userId;
    }

    private String buildIndexKey(String platform, Long channelId) {
        return keyPrefix() + ":bot-session-index:" + normalizedPlatform(platform) + ":channel:" + normalizedChannel(channelId);
    }

    private String keyPrefix() {
        return StringUtils.hasText(properties.getLeaseNamespace()) ? properties.getLeaseNamespace().trim() : "iguana";
    }

    private String normalizedPlatform(String platform) {
        return StringUtils.hasText(platform) ? platform.trim().toLowerCase() : "unknown";
    }

    private String normalizedChannel(Long channelId) {
        return channelId == null ? "0" : Long.toString(channelId);
    }

    private Long parseUserId(String userIdValue) {
        try {
            return Long.valueOf(userIdValue);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize bot session payload.", ex);
        }
    }

    private <T> T deserialize(String rawPayload, Class<T> payloadType) {
        try {
            return objectMapper.readValue(rawPayload, payloadType);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to deserialize bot session payload.", ex);
        }
    }

    private StringRedisTemplate requireRedisTemplate() {
        if (stringRedisTemplate == null) {
            throw new IllegalStateException("Bot session store is configured for Redis, but StringRedisTemplate is unavailable.");
        }
        return stringRedisTemplate;
    }
}
