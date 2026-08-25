package com.example.panel.security;

import com.example.panel.config.RuntimeCoordinationProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.WebUtils;

@Service
public class InternalBotApiRequestGuardService {

    public static final String AUTH_HEADER = "X-Iguana-Bot-Api-Token";
    public static final String TIMESTAMP_HEADER = "X-Iguana-Request-Timestamp";
    public static final String SIGNATURE_HEADER = "X-Iguana-Request-Signature";
    public static final String IDEMPOTENCY_HEADER = "X-Iguana-Idempotency-Key";

    private static final DefaultRedisScript<Long> CLAIM_SCRIPT = new DefaultRedisScript<>(
        """
        local current = redis.call('get', KEYS[1])
        if not current then
            redis.call('set', KEYS[1], 'inflight:' .. ARGV[1], 'PX', ARGV[2])
            return 1
        end
        if string.sub(current, 1, 5) == 'done:' then
            return 2
        end
        return 0
        """,
        Long.class
    );

    private static final DefaultRedisScript<Long> STORE_SCRIPT = new DefaultRedisScript<>(
        """
        if redis.call('get', KEYS[1]) == 'inflight:' .. ARGV[1] then
            redis.call('set', KEYS[1], 'done:' .. ARGV[2], 'PX', ARGV[3])
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

    private final InternalBotApiProperties properties;
    private final RuntimeCoordinationProperties coordinationProperties;
    private final ObjectMapper objectMapper;
    private final StringRedisTemplate stringRedisTemplate;
    private final ConcurrentHashMap<String, LocalIdempotencyEntry> localIdempotencyCache = new ConcurrentHashMap<>();

    public InternalBotApiRequestGuardService(InternalBotApiProperties properties,
                                             RuntimeCoordinationProperties coordinationProperties,
                                             ObjectMapper objectMapper,
                                             ObjectProvider<StringRedisTemplate> stringRedisTemplateProvider) {
        this.properties = properties;
        this.coordinationProperties = coordinationProperties;
        this.objectMapper = objectMapper;
        this.stringRedisTemplate = stringRedisTemplateProvider.getIfAvailable();
    }

    public void authorize(HttpServletRequest request, String token) {
        authorizeInternal(request, token);
    }

    public WriteExecution prepareWrite(HttpServletRequest request, String token) {
        authorizeInternal(request, token);
        String idempotencyKey = normalize(request != null ? request.getHeader(IDEMPOTENCY_HEADER) : null);
        if (!StringUtils.hasText(idempotencyKey) || request == null) {
            return new WriteExecution(null, null);
        }
        String cacheKey = buildIdempotencyCacheKey(request, idempotencyKey);
        String claimToken = java.util.UUID.randomUUID().toString();
        IdempotencyClaim claim = claim(cacheKey, claimToken);
        if (claim.status() == IdempotencyStatus.REPLAY) {
            CachedResponse cachedResponse = loadCachedResponse(cacheKey);
            if (cachedResponse == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Internal bot API idempotency cache is inconsistent.");
            }
            return new WriteExecution(claim, ResponseEntity.status(cachedResponse.status())
                .contentType(MediaType.APPLICATION_JSON)
                .body(cachedResponse.body()));
        }
        if (claim.status() == IdempotencyStatus.IN_FLIGHT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Internal bot API request with the same idempotency key is still in flight.");
        }
        return new WriteExecution(claim, null);
    }

    public ResponseEntity<String> successResponse(WriteExecution execution, Object body) {
        try {
            String json = objectMapper.writeValueAsString(body);
            if (execution != null) {
                execution.markProcessed(json);
            }
            return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
        } catch (JsonProcessingException ex) {
            if (execution != null) {
                execution.release();
            }
            throw new IllegalStateException("Unable to serialize internal bot API response.", ex);
        }
    }

    private void authorizeInternal(HttpServletRequest request, String token) {
        if (!StringUtils.hasText(token) || !Objects.equals(token, properties.getToken())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized internal bot API request");
        }

        String timestamp = normalize(request != null ? request.getHeader(TIMESTAMP_HEADER) : null);
        String signature = normalize(request != null ? request.getHeader(SIGNATURE_HEADER) : null);
        boolean signatureRequired = properties.isRequireRequestSignature();
        boolean signaturePresented = StringUtils.hasText(timestamp) || StringUtils.hasText(signature);
        if (!signatureRequired && !signaturePresented) {
            return;
        }
        if (!StringUtils.hasText(timestamp) || !StringUtils.hasText(signature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Signed internal bot API request is missing timestamp or signature.");
        }

        Instant requestTime = parseTimestamp(timestamp);
        Duration skew = safeDuration(properties.getRequestTimestampSkew(), Duration.ofMinutes(5));
        long driftMillis = Math.abs(Duration.between(requestTime, Instant.now()).toMillis());
        if (driftMillis > skew.toMillis()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal bot API request timestamp is outside the allowed skew window.");
        }

        byte[] body = extractCachedBody(request);
        String canonicalPath = canonicalPath(request);
        String expectedSignature = sign(
            request != null ? request.getMethod() : "GET",
            canonicalPath,
            timestamp,
            body
        );
        if (!constantTimeEquals(expectedSignature, signature)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal bot API request signature is invalid.");
        }
    }

    private IdempotencyClaim claim(String cacheKey, String claimToken) {
        Duration inflightTtl = safeDuration(properties.getIdempotencyInflightTtl(), Duration.ofMinutes(2));
        if (!useRedisCache()) {
            cleanupLocalCache(cacheKey);
            LocalIdempotencyEntry existing = localIdempotencyCache.putIfAbsent(
                cacheKey,
                new LocalIdempotencyEntry("inflight:" + claimToken, Instant.now().plus(inflightTtl))
            );
            if (existing == null) {
                return new IdempotencyClaim(cacheKey, claimToken, IdempotencyStatus.ACQUIRED);
            }
            if (existing.value().startsWith("done:")) {
                return new IdempotencyClaim(cacheKey, claimToken, IdempotencyStatus.REPLAY);
            }
            return new IdempotencyClaim(cacheKey, claimToken, IdempotencyStatus.IN_FLIGHT);
        }

        Long claimed = stringRedisTemplate.execute(
            CLAIM_SCRIPT,
            List.of(cacheKey),
            claimToken,
            Long.toString(inflightTtl.toMillis())
        );
        if (claimed != null && claimed == 1L) {
            return new IdempotencyClaim(cacheKey, claimToken, IdempotencyStatus.ACQUIRED);
        }
        if (claimed != null && claimed == 2L) {
            return new IdempotencyClaim(cacheKey, claimToken, IdempotencyStatus.REPLAY);
        }
        return new IdempotencyClaim(cacheKey, claimToken, IdempotencyStatus.IN_FLIGHT);
    }

    private void storeProcessed(IdempotencyClaim claim, String responseBody) {
        if (claim == null || claim.status() != IdempotencyStatus.ACQUIRED) {
            return;
        }
        Duration doneTtl = safeDuration(properties.getIdempotencyTtl(), Duration.ofHours(12));
        String encodedPayload = encodeCachedResponse(new CachedResponse(200, responseBody));
        if (!useRedisCache()) {
            localIdempotencyCache.put(
                claim.cacheKey(),
                new LocalIdempotencyEntry("done:" + encodedPayload, Instant.now().plus(doneTtl))
            );
            return;
        }
        stringRedisTemplate.execute(
            STORE_SCRIPT,
            List.of(claim.cacheKey()),
            claim.claimToken(),
            encodedPayload,
            Long.toString(doneTtl.toMillis())
        );
    }

    private void release(IdempotencyClaim claim) {
        if (claim == null || claim.status() != IdempotencyStatus.ACQUIRED) {
            return;
        }
        if (!useRedisCache()) {
            localIdempotencyCache.computeIfPresent(claim.cacheKey(), (ignored, existing) ->
                Objects.equals(existing.value(), "inflight:" + claim.claimToken()) ? null : existing
            );
            return;
        }
        stringRedisTemplate.execute(RELEASE_SCRIPT, List.of(claim.cacheKey()), claim.claimToken());
    }

    private CachedResponse loadCachedResponse(String cacheKey) {
        String value;
        if (!useRedisCache()) {
            cleanupLocalCache(cacheKey);
            LocalIdempotencyEntry entry = localIdempotencyCache.get(cacheKey);
            value = entry != null ? entry.value() : null;
        } else {
            value = stringRedisTemplate.opsForValue().get(cacheKey);
        }
        if (!StringUtils.hasText(value) || !value.startsWith("done:")) {
            return null;
        }
        return decodeCachedResponse(value.substring(5));
    }

    private void cleanupLocalCache(String cacheKey) {
        if (!StringUtils.hasText(cacheKey)) {
            return;
        }
        LocalIdempotencyEntry entry = localIdempotencyCache.get(cacheKey);
        if (entry != null && !entry.expiresAt().isAfter(Instant.now())) {
            localIdempotencyCache.remove(cacheKey, entry);
        }
    }

    private boolean useRedisCache() {
        return coordinationProperties.isRedisMode() && stringRedisTemplate != null;
    }

    private String buildIdempotencyCacheKey(HttpServletRequest request, String idempotencyKey) {
        String namespace = StringUtils.hasText(coordinationProperties.getLeaseNamespace())
            ? coordinationProperties.getLeaseNamespace().trim()
            : "iguana";
        String seed = (request.getMethod() + "\n" + canonicalPath(request) + "\n" + idempotencyKey.trim()).toLowerCase();
        return namespace + ":panel-internal-api:idempotency:" + DigestUtils.md5DigestAsHex(seed.getBytes(StandardCharsets.UTF_8));
    }

    private String canonicalPath(HttpServletRequest request) {
        if (request == null) {
            return "/";
        }
        String uri = Objects.toString(request.getRequestURI(), "/");
        String query = request.getQueryString();
        return StringUtils.hasText(query) ? uri + "?" + query : uri;
    }

    private byte[] extractCachedBody(HttpServletRequest request) {
        ContentCachingRequestWrapper wrapper = WebUtils.getNativeRequest(request, ContentCachingRequestWrapper.class);
        if (wrapper == null) {
            return new byte[0];
        }
        byte[] body = wrapper.getContentAsByteArray();
        return body != null ? body : new byte[0];
    }

    private Instant parseTimestamp(String timestamp) {
        try {
            return OffsetDateTime.parse(timestamp).toInstant();
        } catch (DateTimeParseException ex) {
            try {
                return Instant.ofEpochMilli(Long.parseLong(timestamp));
            } catch (NumberFormatException numberFormatException) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Internal bot API request timestamp format is invalid.");
            }
        }
    }

    private String sign(String method, String canonicalPath, String timestamp, byte[] body) {
        String normalizedMethod = StringUtils.hasText(method) ? method.trim().toUpperCase() : "GET";
        String normalizedPath = StringUtils.hasText(canonicalPath) ? canonicalPath.trim() : "/";
        String normalizedTimestamp = StringUtils.hasText(timestamp) ? timestamp.trim() : OffsetDateTime.now(ZoneOffset.UTC).toString();
        byte[] payloadBytes = body != null ? body : new byte[0];
        String bodyDigest = sha256Hex(payloadBytes);
        String canonical = normalizedMethod + "\n" + normalizedPath + "\n" + normalizedTimestamp + "\n" + bodyDigest;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(resolveSignatureSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to compute internal bot API request signature.", ex);
        }
    }

    private String resolveSignatureSecret() {
        return StringUtils.hasText(properties.getSignatureSecret())
            ? properties.getSignatureSecret().trim()
            : Objects.toString(properties.getToken(), "");
    }

    private boolean constantTimeEquals(String left, String right) {
        byte[] leftBytes = Objects.toString(left, "").getBytes(StandardCharsets.UTF_8);
        byte[] rightBytes = Objects.toString(right, "").getBytes(StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(leftBytes, rightBytes);
    }

    private String sha256Hex(byte[] value) {
        try {
            return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value != null ? value : new byte[0]));
        } catch (GeneralSecurityException ex) {
            throw new IllegalStateException("Unable to compute SHA-256 digest for internal bot API request.", ex);
        }
    }

    private String encodeCachedResponse(CachedResponse response) {
        try {
            return Base64.getUrlEncoder().encodeToString(objectMapper.writeValueAsBytes(response));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Unable to serialize internal bot API cached response.", ex);
        }
    }

    private CachedResponse decodeCachedResponse(String encoded) {
        if (!StringUtils.hasText(encoded)) {
            return null;
        }
        try {
            byte[] bytes = Base64.getUrlDecoder().decode(encoded);
            return objectMapper.readValue(bytes, CachedResponse.class);
        } catch (Exception ex) {
            return null;
        }
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Duration safeDuration(Duration value, Duration fallback) {
        if (value == null || value.isNegative() || value.isZero()) {
            return fallback;
        }
        return value;
    }

    public final class WriteExecution {

        private final IdempotencyClaim claim;
        private final ResponseEntity<String> replayResponse;

        private WriteExecution(IdempotencyClaim claim, ResponseEntity<String> replayResponse) {
            this.claim = claim;
            this.replayResponse = replayResponse;
        }

        public ResponseEntity<String> replayResponse() {
            return replayResponse;
        }

        public void markProcessed(String responseBody) {
            storeProcessed(claim, responseBody);
        }

        public void release() {
            InternalBotApiRequestGuardService.this.release(claim);
        }
    }

    private record CachedResponse(int status, String body) {
    }

    private record IdempotencyClaim(String cacheKey, String claimToken, IdempotencyStatus status) {
    }

    private enum IdempotencyStatus {
        ACQUIRED,
        REPLAY,
        IN_FLIGHT
    }

    private record LocalIdempotencyEntry(String value, Instant expiresAt) {
    }
}
