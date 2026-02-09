package com.example.panel.controller;

import com.example.panel.entity.ClientBlacklist;
import com.example.panel.repository.ClientBlacklistRepository;
import com.example.panel.service.BlacklistHistoryService;
import com.example.panel.service.DialogNotificationService;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/blacklist")
@PreAuthorize("hasAuthority('PAGE_CLIENTS')")
public class ClientBlacklistApiController {

    private static final Logger log = LoggerFactory.getLogger(ClientBlacklistApiController.class);

    private final ClientBlacklistRepository repository;
    private final BlacklistHistoryService blacklistHistoryService;
    private final DialogNotificationService dialogNotificationService;

    public ClientBlacklistApiController(ClientBlacklistRepository repository,
                                        BlacklistHistoryService blacklistHistoryService,
                                        DialogNotificationService dialogNotificationService) {
        this.repository = repository;
        this.blacklistHistoryService = blacklistHistoryService;
        this.dialogNotificationService = dialogNotificationService;
    }

    @PostMapping("/add")
    @Transactional
    public Map<String, Object> add(@RequestParam("user_id") String userId,
                                   @RequestParam(value = "reason", required = false) String reason,
                                   Authentication authentication) {
        if (!StringUtils.hasText(userId)) {
            return Map.of("ok", false, "error", "Не указан клиент");
        }
        ClientBlacklist entry = repository.findById(userId)
                .orElseGet(() -> {
                    ClientBlacklist fresh = new ClientBlacklist();
                    fresh.setUserId(userId);
                    return fresh;
                });
        OffsetDateTime now = OffsetDateTime.now();
        entry.setBlacklisted(true);
        entry.setReason(StringUtils.hasText(reason) ? reason.trim() : null);
        entry.setAddedAt(now);
        entry.setAddedBy(authentication != null ? authentication.getName() : "system");
        entry.setUnblockRequested(false);
        entry.setUnblockRequestedAt(null);
        repository.save(entry);
        blacklistHistoryService.recordEvent(
                userId,
                "blocked",
                entry.getReason(),
                entry.getAddedBy(),
                now
        );
        log.info("Client {} added to blacklist by {}", userId, entry.getAddedBy());
        return Map.of("ok", true, "message", "Клиент добавлен в blacklist");
    }

    @PostMapping("/remove")
    @Transactional
    public Map<String, Object> remove(@RequestParam("user_id") String userId,
                                      Authentication authentication) {
        if (!StringUtils.hasText(userId)) {
            return Map.of("ok", false, "error", "Не указан клиент");
        }
        Optional<ClientBlacklist> entryOpt = repository.findById(userId);
        ClientBlacklist entry = entryOpt.orElseGet(() -> {
            ClientBlacklist fresh = new ClientBlacklist();
            fresh.setUserId(userId);
            return fresh;
        });
        OffsetDateTime now = OffsetDateTime.now();
        entry.setBlacklisted(false);
        entry.setReason(null);
        entry.setAddedBy(authentication != null ? authentication.getName() : "system");
        entry.setUnblockRequested(false);
        entry.setUnblockRequestedAt(null);
        repository.save(entry);
        blacklistHistoryService.recordEvent(
                userId,
                "unblocked",
                null,
                entry.getAddedBy(),
                now
        );
        String duration = blacklistHistoryService.calculateDurationFromLastBlock(userId, now)
                .map(blacklistHistoryService::formatDuration)
                .orElse(null);
        String text = duration == null
                ? "Ваш аккаунт разблокирован."
                : "Ваш аккаунт разблокирован. В блокировке: " + duration + ".";
        dialogNotificationService.notifyUserByLastChannel(Long.valueOf(userId), text, true);
        log.info("Client {} removed from blacklist by {}", userId, entry.getAddedBy());
        return Map.of("ok", true, "message", "Клиент разблокирован");
    }
}
