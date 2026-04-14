package com.example.supportbot.service;

import com.example.supportbot.entity.Channel;
import com.example.supportbot.entity.Feedback;
import com.example.supportbot.entity.PendingFeedbackRequest;
import com.example.supportbot.repository.FeedbackRepository;
import com.example.supportbot.repository.PendingFeedbackRequestRepository;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FeedbackService {

    private final PendingFeedbackRequestRepository pendingFeedbackRequestRepository;
    private final FeedbackRepository feedbackRepository;

    public FeedbackService(PendingFeedbackRequestRepository pendingFeedbackRequestRepository,
                           FeedbackRepository feedbackRepository) {
        this.pendingFeedbackRequestRepository = pendingFeedbackRequestRepository;
        this.feedbackRepository = feedbackRepository;
    }

    @Transactional(readOnly = true)
    public Optional<PendingFeedbackRequest> findActiveRequest(long userId, Channel channel) {
        OffsetDateTime now = OffsetDateTime.now();
        Optional<PendingFeedbackRequest> request = Optional.empty();
        if (channel != null && channel.getId() != null) {
            request = pendingFeedbackRequestRepository
                    .findFirstByUserIdAndChannel_IdAndExpiresAtAfterOrderByCreatedAtDesc(userId, channel.getId(), now);
        }
        if (request.isPresent()) {
            return request;
        }
        return pendingFeedbackRequestRepository
                .findFirstByUserIdAndExpiresAtAfterOrderByCreatedAtDesc(userId, now);
    }

    @Transactional
    public void storeFeedback(PendingFeedbackRequest request, int rating) {
        Channel channel = request.getChannel();

        Feedback feedback = new Feedback();
        feedback.setUserId(request.getUserId());
        feedback.setChannel(channel);
        feedback.setTicketId(request.getTicketId());
        feedback.setRating(rating);
        feedback.setTimestamp(OffsetDateTime.now());
        feedbackRepository.save(feedback);

        request.setExpiresAt(OffsetDateTime.now());
        pendingFeedbackRequestRepository.save(request);
    }
}
