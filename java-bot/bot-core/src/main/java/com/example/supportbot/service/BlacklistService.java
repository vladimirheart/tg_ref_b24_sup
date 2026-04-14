package com.example.supportbot.service;

import com.example.supportbot.entity.ClientBlacklist;
import com.example.supportbot.entity.ClientUnblockRequest;
import com.example.supportbot.repository.ClientBlacklistRepository;
import com.example.supportbot.repository.ClientUnblockRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;

@Service
public class BlacklistService {

    private static final Logger log = LoggerFactory.getLogger(BlacklistService.class);

    private final ClientBlacklistRepository blacklistRepository;
    private final ClientUnblockRequestRepository unblockRequestRepository;

    public BlacklistService(ClientBlacklistRepository blacklistRepository,
                            ClientUnblockRequestRepository unblockRequestRepository) {
        this.blacklistRepository = blacklistRepository;
        this.unblockRequestRepository = unblockRequestRepository;
    }

    @Transactional(readOnly = true)
    public BlacklistStatus getStatus(long userId) {
        return blacklistRepository.findById(String.valueOf(userId))
                .map(entity -> new BlacklistStatus(entity.isBlacklisted(), entity.isUnblockRequested()))
                .orElseGet(() -> new BlacklistStatus(false, false));
    }

    @Transactional
    public ClientUnblockRequest registerUnblockRequest(long userId, String reason, Long channelId) {
        String key = String.valueOf(userId);
        OffsetDateTime now = OffsetDateTime.now();

        ClientBlacklist blacklist = blacklistRepository.findById(key)
                .orElseGet(() -> {
                    ClientBlacklist created = new ClientBlacklist();
                    created.setUserId(key);
                    created.setBlacklisted(true);
                    created.setUnblockRequested(false);
                    return created;
                });

        blacklist.setBlacklisted(true);
        blacklist.setUnblockRequested(true);
        blacklist.setUnblockRequestedAt(now);
        blacklistRepository.save(blacklist);

        ClientUnblockRequest request = unblockRequestRepository
                .findFirstByUserIdAndStatusOrderByIdDesc(key, "pending")
                .orElseGet(ClientUnblockRequest::new);

        request.setUserId(key);
        request.setChannelId(channelId);
        request.setReason(reason);
        request.setCreatedAt(now);
        request.setStatus("pending");
        request.setDecidedAt(null);
        request.setDecidedBy(null);
        request.setDecisionComment(null);

        ClientUnblockRequest saved = unblockRequestRepository.save(request);
        log.info("Stored unblock request for user {}", userId);
        return saved;
    }

    @Transactional
    public UnblockRequestDecision requestUnblock(long userId, String reason, Long channelId, Duration cooldown) {
        String key = String.valueOf(userId);
        OffsetDateTime now = OffsetDateTime.now();

        Optional<ClientBlacklist> existingOpt = blacklistRepository.findById(key);
        OffsetDateTime lastRequestedAt = existingOpt.map(ClientBlacklist::getUnblockRequestedAt).orElse(null);
        if (cooldown != null && !cooldown.isZero() && !cooldown.isNegative() && lastRequestedAt != null) {
            OffsetDateTime nextAllowed = lastRequestedAt.plus(cooldown);
            if (nextAllowed.isAfter(now)) {
                ClientUnblockRequest existingRequest = unblockRequestRepository
                        .findFirstByUserIdAndStatusOrderByIdDesc(key, "pending")
                        .orElseGet(() -> unblockRequestRepository.findFirstByUserIdOrderByIdDesc(key).orElse(null));
                return new UnblockRequestDecision(existingRequest, false, Duration.between(now, nextAllowed));
            }
        }

        ClientUnblockRequest saved = registerUnblockRequest(userId, reason, channelId);
        return new UnblockRequestDecision(saved, true, Duration.ZERO);
    }

    public record BlacklistStatus(boolean blacklisted, boolean unblockRequested) {}

    public record UnblockRequestDecision(ClientUnblockRequest request, boolean created, Duration retryAfter) {}
}
