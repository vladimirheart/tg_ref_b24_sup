package com.example.supportbot.service;

import com.example.supportbot.entity.Channel;
import com.example.supportbot.entity.Feedback;
import com.example.supportbot.entity.PendingFeedbackRequest;
import com.example.supportbot.repository.FeedbackRepository;
import com.example.supportbot.repository.PendingFeedbackRequestRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class FeedbackService {

    private static final Logger log = LoggerFactory.getLogger(FeedbackService.class);

    private final PendingFeedbackRequestRepository pendingFeedbackRequestRepository;
    private final FeedbackRepository feedbackRepository;
    private final UiEventOutboxService uiEventOutboxService;

    public FeedbackService(PendingFeedbackRequestRepository pendingFeedbackRequestRepository,
                           FeedbackRepository feedbackRepository,
                           UiEventOutboxService uiEventOutboxService) {
        this.pendingFeedbackRequestRepository = pendingFeedbackRequestRepository;
        this.feedbackRepository = feedbackRepository;
        this.uiEventOutboxService = uiEventOutboxService;
    }

    @Transactional(readOnly = true)
    public Optional<PendingFeedbackRequest> findActiveRequest(long userId, Channel channel) {
        OffsetDateTime now = OffsetDateTime.now();
        Optional<PendingFeedbackRequest> request = Optional.empty();
        if (channel != null && channel.getId() != null) {
            request = pendingFeedbackRequestRepository
                    .findFirstByUserIdAndChannel_IdAndExpiresAtAfterOrderByCreatedAtDesc(userId, channel.getId(), now);
        }
        if (request.isPresent() && !hasStoredFeedback(request.get())) {
            return request;
        }
        Optional<PendingFeedbackRequest> fallback = pendingFeedbackRequestRepository
                .findFirstByUserIdAndExpiresAtAfterOrderByCreatedAtDesc(userId, now);
        if (fallback.isPresent() && hasStoredFeedback(fallback.get())) {
            return Optional.empty();
        }
        return fallback;
    }

    public void storeFeedback(PendingFeedbackRequest request, int rating) {
        Channel channel = request.getChannel();
        String ticketId = request.getTicketId();
        OffsetDateTime now = OffsetDateTime.now();

        Feedback feedback = resolveFeedbackRecord(ticketId);
        feedback.setUserId(request.getUserId());
        feedback.setChannel(channel);
        feedback.setTicketId(ticketId);
        feedback.setRating(rating);
        feedback.setTimestamp(now);
        feedbackRepository.save(feedback);
        uiEventOutboxService.publishFeedbackCreated(ticketId, channel, rating);
        expireRequestQuietly(request, now);
    }

    private boolean hasStoredFeedback(PendingFeedbackRequest request) {
        if (request == null || !StringUtils.hasText(request.getTicketId())) {
            return false;
        }
        return feedbackRepository.existsByTicketId(request.getTicketId());
    }

    private Feedback resolveFeedbackRecord(String ticketId) {
        if (StringUtils.hasText(ticketId)) {
            return feedbackRepository.findFirstByTicketIdOrderByTimestampDesc(ticketId).orElseGet(Feedback::new);
        }
        return new Feedback();
    }

    private void expireRequestQuietly(PendingFeedbackRequest request, OffsetDateTime expiresAt) {
        if (request == null) {
            return;
        }
        request.setExpiresAt(expiresAt);
        try {
            pendingFeedbackRequestRepository.save(request);
        } catch (RuntimeException ex) {
            log.warn("Saved feedback for ticket {}, but failed to expire pending feedback request {}: {}",
                    request.getTicketId(),
                    request.getId(),
                    ex.getMessage());
        }
    }
}
