package com.example.supportbot.max;

import com.example.supportbot.entity.ClientAvatarHistory;
import com.example.supportbot.repository.ClientAvatarHistoryRepository;
import com.example.supportbot.service.AttachmentService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PreDestroy;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class MaxClientAvatarSyncService {

    private static final Logger log = LoggerFactory.getLogger(MaxClientAvatarSyncService.class);
    private static final String SOURCE = "max";
    private static final Duration REFRESH_TTL = Duration.ofHours(1);
    private static final Duration RETRY_BACKOFF = Duration.ofMinutes(5);
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(".jpg", ".jpeg", ".png", ".gif", ".webp");

    private final MaxApiClient maxApiClient;
    private final AttachmentService attachmentService;
    private final ClientAvatarHistoryRepository avatarHistoryRepository;
    private final ObjectMapper objectMapper;
    private final ThreadPoolExecutor executor;
    private final Map<Long, Instant> refreshAfter = new ConcurrentHashMap<>();
    private final Set<Long> inFlight = ConcurrentHashMap.newKeySet();

    public MaxClientAvatarSyncService(MaxApiClient maxApiClient,
                                      AttachmentService attachmentService,
                                      ClientAvatarHistoryRepository avatarHistoryRepository,
                                      ObjectMapper objectMapper) {
        this.maxApiClient = maxApiClient;
        this.attachmentService = attachmentService;
        this.avatarHistoryRepository = avatarHistoryRepository;
        this.objectMapper = objectMapper;
        AtomicInteger sequence = new AtomicInteger();
        this.executor = new ThreadPoolExecutor(
            1,
            2,
            30L,
            TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(128),
            runnable -> {
                Thread thread = new Thread(runnable, "max-avatar-sync-" + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            },
            new ThreadPoolExecutor.AbortPolicy()
        );
        this.executor.allowCoreThreadTimeOut(true);
    }

    public void schedule(Long userId, Long chatId) {
        if (userId == null || userId <= 0 || chatId == null || chatId <= 0) {
            return;
        }
        Instant now = Instant.now();
        Instant next = refreshAfter.get(userId);
        if (next != null && now.isBefore(next)) {
            return;
        }
        if (!inFlight.add(userId)) {
            return;
        }
        try {
            executor.execute(() -> {
                boolean success = false;
                try {
                    success = syncNow(userId, chatId);
                } catch (Exception ex) {
                    log.warn("MAX avatar sync failed for user {} chat {}: {}", userId, chatId, ex.getMessage());
                } finally {
                    refreshAfter.put(userId, Instant.now().plus(success ? REFRESH_TTL : RETRY_BACKOFF));
                    inFlight.remove(userId);
                }
            });
        } catch (RejectedExecutionException ex) {
            inFlight.remove(userId);
            refreshAfter.put(userId, now.plus(RETRY_BACKOFF));
            log.warn("MAX avatar sync queue is full; user {} will retry later", userId);
        }
    }

    boolean syncNow(Long userId, Long chatId) {
        Optional<MaxApiClient.MaxDialogUser> profileOptional = maxApiClient.fetchDialogUser(chatId);
        if (profileOptional.isEmpty()) {
            return false;
        }
        MaxApiClient.MaxDialogUser profile = profileOptional.get();
        if (profile.userId() != userId.longValue()) {
            log.warn("MAX dialog profile user mismatch: message user={} profile user={} chat={}", userId, profile.userId(), chatId);
            return false;
        }

        String thumbUrl = clean(profile.avatarUrl());
        String fullUrl = clean(profile.fullAvatarUrl());
        OffsetDateTime now = OffsetDateTime.now();
        if (thumbUrl == null && fullUrl == null) {
            return true;
        }

        MaxApiClient.DownloadedAvatar thumb = download(thumbUrl, userId, false);
        MaxApiClient.DownloadedAvatar full = fullUrl != null && fullUrl.equals(thumbUrl)
            ? thumb
            : download(fullUrl, userId, true);

        StoredEvidence thumbStored = store(userId, false, thumb);
        StoredEvidence fullStored = store(userId, true, full);
        if (thumbStored == null && fullStored == null) {
            return false;
        }

        String fingerprint = fingerprint(thumbStored, fullStored);
        int totalSize = safeSize(thumbStored) + safeSize(fullStored);
        persistHistory(
            userId,
            fingerprint,
            thumbStored != null ? thumbStored.storedName() : null,
            fullStored != null ? fullStored.storedName() : null,
            totalSize,
            chatId,
            now,
            "stored"
        );
        return true;
    }

    private MaxApiClient.DownloadedAvatar download(String url, Long userId, boolean full) {
        if (!StringUtils.hasText(url)) {
            return null;
        }
        try {
            return maxApiClient.downloadAvatar(url);
        } catch (Exception ex) {
            log.warn("Unable to download MAX {} avatar for user {}: {}", full ? "full" : "thumb", userId, ex.getMessage());
            return null;
        }
    }

    private StoredEvidence store(long userId, boolean full, MaxApiClient.DownloadedAvatar avatar) {
        if (avatar == null || avatar.bytes() == null || avatar.bytes().length == 0) {
            return null;
        }
        String extension = resolveExtension(avatar.contentType(), avatar.filename());
        if (extension == null) {
            log.warn("Unsupported MAX avatar content for user {}: type={} filename={}", userId, avatar.contentType(), avatar.filename());
            return null;
        }
        try (ByteArrayInputStream input = new ByteArrayInputStream(avatar.bytes())) {
            AttachmentService.StoredAvatar stored = attachmentService.storeClientAvatar(
                userId, full, extension, avatar.contentType(), input);
            return new StoredEvidence(stored.storedName(), avatar.bytes());
        } catch (Exception ex) {
            log.warn("Unable to store MAX {} avatar for user {}: {}", full ? "full" : "thumb", userId, ex.getMessage());
            return null;
        }
    }

    private String resolveExtension(String contentType, String filename) {
        String normalizedType = clean(contentType);
        if (normalizedType != null) {
            normalizedType = normalizedType.toLowerCase();
            int separator = normalizedType.indexOf(';');
            if (separator >= 0) {
                normalizedType = normalizedType.substring(0, separator).trim();
            }
            return switch (normalizedType) {
                case "image/jpeg", "image/jpg" -> ".jpg";
                case "image/png" -> ".png";
                case "image/gif" -> ".gif";
                case "image/webp" -> ".webp";
                default -> extensionFromFilename(filename);
            };
        }
        return extensionFromFilename(filename);
    }

    private String extensionFromFilename(String filename) {
        String normalized = clean(filename);
        if (normalized == null) {
            return null;
        }
        int dot = normalized.lastIndexOf('.');
        if (dot < 0) {
            return null;
        }
        String extension = normalized.substring(dot).toLowerCase();
        return ALLOWED_EXTENSIONS.contains(extension) ? extension : null;
    }

    private String fingerprint(StoredEvidence thumb, StoredEvidence full) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update("max-avatar-v1".getBytes(StandardCharsets.UTF_8));
            if (thumb != null) {
                digest.update((byte) 1);
                digest.update(thumb.bytes());
            }
            if (full != null) {
                digest.update((byte) 2);
                digest.update(full.bytes());
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to hash MAX avatar", ex);
        }
    }

    private void persistHistory(Long userId,
                                String fingerprint,
                                String thumbPath,
                                String fullPath,
                                int fileSize,
                                Long chatId,
                                OffsetDateTime now,
                                String result) {
        ClientAvatarHistory history = avatarHistoryRepository.findByUserIdAndFingerprint(userId, fingerprint)
            .orElseGet(ClientAvatarHistory::new);
        history.setUserId(userId);
        history.setFingerprint(fingerprint);
        history.setSource(SOURCE);
        history.setThumbPath(thumbPath);
        history.setFullPath(fullPath);
        history.setFileSize(fileSize);
        history.setFetchedAt(now);
        history.setLastSeenAt(now);
        history.setMetadata(metadata(chatId, result, thumbPath != null, fullPath != null));
        try {
            avatarHistoryRepository.save(history);
        } catch (DataIntegrityViolationException ex) {
            log.debug("Concurrent MAX avatar history write for user {} fingerprint {}", userId, fingerprint);
        }
    }

    private String metadata(Long chatId, String result, boolean hasThumb, boolean hasFull) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("provider", SOURCE);
        metadata.put("chat_id", chatId);
        metadata.put("result", result);
        metadata.put("has_thumb", hasThumb);
        metadata.put("has_full", hasFull);
        try {
            return objectMapper.writeValueAsString(metadata);
        } catch (Exception ex) {
            return "{\"provider\":\"max\"}";
        }
    }

    private int safeSize(StoredEvidence evidence) {
        return evidence == null || evidence.bytes() == null ? 0 : Math.min(Integer.MAX_VALUE, evidence.bytes().length);
    }

    private String clean(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private record StoredEvidence(String storedName, byte[] bytes) {
    }
}
