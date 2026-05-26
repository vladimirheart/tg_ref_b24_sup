package com.example.panel.service;

import com.example.panel.entity.ClientBlacklist;
import com.example.panel.repository.ClientBlacklistRepository;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class ClientBlacklistService {

    private static final Logger log = LoggerFactory.getLogger(ClientBlacklistService.class);

    private final ClientBlacklistRepository repository;
    private final BlacklistHistoryService blacklistHistoryService;
    private final DialogNotificationService dialogNotificationService;

    public ClientBlacklistService(ClientBlacklistRepository repository,
                                  BlacklistHistoryService blacklistHistoryService,
                                  DialogNotificationService dialogNotificationService) {
        this.repository = repository;
        this.blacklistHistoryService = blacklistHistoryService;
        this.dialogNotificationService = dialogNotificationService;
    }

    @Transactional
    public BlacklistMutationResult blockClient(String userId,
                                               String reason,
                                               String actor,
                                               boolean notifyClient) {
        if (!StringUtils.hasText(userId)) {
            return BlacklistMutationResult.failure("Не указан клиент");
        }
        String normalizedUserId = userId.trim();
        String normalizedActor = StringUtils.hasText(actor) ? actor.trim() : "system";
        String normalizedReason = StringUtils.hasText(reason) ? reason.trim() : null;

        ClientBlacklist entry = repository.findById(normalizedUserId)
                .orElseGet(() -> {
                    ClientBlacklist fresh = new ClientBlacklist();
                    fresh.setUserId(normalizedUserId);
                    return fresh;
                });

        OffsetDateTime now = OffsetDateTime.now();
        entry.setBlacklisted(true);
        entry.setReason(normalizedReason);
        entry.setAddedAt(now);
        entry.setAddedBy(normalizedActor);
        entry.setUnblockRequested(false);
        entry.setUnblockRequestedAt(null);
        repository.save(entry);

        blacklistHistoryService.recordEvent(
                normalizedUserId,
                "blocked",
                normalizedReason,
                normalizedActor,
                now
        );

        if (notifyClient) {
            notifyClientSafely(normalizedUserId, "Ваш аккаунт временно заблокирован.");
        }

        log.info("Client {} added to blacklist by {}", normalizedUserId, normalizedActor);
        return BlacklistMutationResult.success("Клиент добавлен в blacklist");
    }

    @Transactional
    public BlacklistMutationResult unblockClient(String userId,
                                                 String actor,
                                                 boolean notifyClient) {
        if (!StringUtils.hasText(userId)) {
            return BlacklistMutationResult.failure("Не указан клиент");
        }
        String normalizedUserId = userId.trim();
        String normalizedActor = StringUtils.hasText(actor) ? actor.trim() : "system";

        ClientBlacklist entry = repository.findById(normalizedUserId)
                .orElseGet(() -> {
                    ClientBlacklist fresh = new ClientBlacklist();
                    fresh.setUserId(normalizedUserId);
                    return fresh;
                });

        OffsetDateTime now = OffsetDateTime.now();
        entry.setBlacklisted(false);
        entry.setReason(null);
        entry.setAddedBy(normalizedActor);
        entry.setUnblockRequested(false);
        entry.setUnblockRequestedAt(null);
        repository.save(entry);

        blacklistHistoryService.recordEvent(
                normalizedUserId,
                "unblocked",
                null,
                normalizedActor,
                now
        );

        if (notifyClient) {
            String duration = blacklistHistoryService.calculateDurationFromLastBlock(normalizedUserId, now)
                    .map(blacklistHistoryService::formatDuration)
                    .orElse(null);
            String text = duration == null
                    ? "Ваш аккаунт разблокирован."
                    : "Ваш аккаунт разблокирован. В блокировке: " + duration + ".";
            notifyClientSafely(normalizedUserId, text);
        }

        log.info("Client {} removed from blacklist by {}", normalizedUserId, normalizedActor);
        return BlacklistMutationResult.success("Клиент разблокирован");
    }

    private void notifyClientSafely(String userId, String message) {
        Long parsedUserId = parseUserId(userId);
        if (parsedUserId == null) {
            log.warn("Unable to notify blacklisted client {}: invalid numeric user id", userId);
            return;
        }
        dialogNotificationService.notifyUserByLastChannel(parsedUserId, message, true);
    }

    private Long parseUserId(String userId) {
        if (!StringUtils.hasText(userId)) {
            return null;
        }
        try {
            return Long.parseLong(userId.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public record BlacklistMutationResult(boolean ok, String message, String error) {
        static BlacklistMutationResult success(String message) {
            return new BlacklistMutationResult(true, message, null);
        }

        static BlacklistMutationResult failure(String error) {
            return new BlacklistMutationResult(false, null, error);
        }
    }
}
