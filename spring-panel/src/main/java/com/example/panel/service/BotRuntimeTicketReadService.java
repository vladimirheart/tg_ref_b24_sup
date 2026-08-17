package com.example.panel.service;

import com.example.panel.entity.Feedback;
import com.example.panel.entity.Message;
import com.example.panel.entity.PendingFeedbackRequest;
import com.example.panel.entity.Ticket;
import com.example.panel.entity.TicketActive;
import com.example.panel.repository.FeedbackRepository;
import com.example.panel.repository.MessageRepository;
import com.example.panel.repository.PendingFeedbackRequestRepository;
import com.example.panel.repository.TicketActiveRepository;
import com.example.panel.repository.TicketRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class BotRuntimeTicketReadService {

    private static final DateTimeFormatter CLIENT_TICKET_NUMBER_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private final MessageRepository messageRepository;
    private final TicketRepository ticketRepository;
    private final TicketActiveRepository ticketActiveRepository;
    private final FeedbackRepository feedbackRepository;
    private final PendingFeedbackRequestRepository pendingFeedbackRequestRepository;

    public BotRuntimeTicketReadService(MessageRepository messageRepository,
                                       TicketRepository ticketRepository,
                                       TicketActiveRepository ticketActiveRepository,
                                       FeedbackRepository feedbackRepository,
                                       PendingFeedbackRequestRepository pendingFeedbackRequestRepository) {
        this.messageRepository = messageRepository;
        this.ticketRepository = ticketRepository;
        this.ticketActiveRepository = ticketActiveRepository;
        this.feedbackRepository = feedbackRepository;
        this.pendingFeedbackRequestRepository = pendingFeedbackRequestRepository;
    }

    @Transactional(readOnly = true)
    public Optional<ActiveTicketLookup> findActiveTicket(Long userId, String username, Long channelId) {
        List<String> identities = buildIdentities(userId, username);
        if (identities.isEmpty()) {
            return Optional.empty();
        }
        List<TicketActive> active = channelId != null
            ? ticketActiveRepository.findByUserIdentityInOrderByLastSeenDescAndChannelId(identities, channelId)
            : ticketActiveRepository.findByUserIdentityInOrderByLastSeenDesc(identities);
        if (active.isEmpty()) {
            return Optional.empty();
        }
        TicketActive ticketActive = active.get(0);
        return Optional.of(new ActiveTicketLookup(
            ticketActive.getTicketId(),
            ticketActive.getUserIdentity(),
            ticketActive.getLastSeen()
        ));
    }

    @Transactional(readOnly = true)
    public Optional<TicketLookup> findTicket(String ticketId) {
        if (!StringUtils.hasText(ticketId)) {
            return Optional.empty();
        }
        return ticketRepository.findByIdTicketId(ticketId.trim())
            .map(ticket -> new TicketLookup(ticket.getUserId(), ticket.getTicketId(), ticket.getStatus()));
    }

    @Transactional(readOnly = true)
    public Optional<LastTicketContext> findLastTicketContext(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        return messageRepository.findTopByUserIdOrderByCreatedAtDesc(userId)
            .map(message -> new LastTicketContext(
                message.getTicketId(),
                message.getBusiness(),
                message.getLocationType(),
                message.getCity(),
                message.getLocationName(),
                message.getProblem(),
                message.getCreatedAt(),
                message.getCreatedDate(),
                message.getId()
            ));
    }

    @Transactional(readOnly = true)
    public Optional<RequestNumberLookup> resolveRequestNumber(String ticketId) {
        if (!StringUtils.hasText(ticketId)) {
            return Optional.empty();
        }
        return messageRepository.findByTicketId(ticketId.trim())
            .map(message -> new RequestNumberLookup(
                message.getTicketId(),
                resolveClientTicketNumber(message)
            ));
    }

    @Transactional(readOnly = true)
    public List<TicketSummaryLookup> findRecentTickets(Long userId, int limit) {
        if (userId == null || limit <= 0) {
            return List.of();
        }
        List<Message> messages = messageRepository.findTop10ByUserIdOrderByCreatedAtDesc(userId);
        if (messages.isEmpty()) {
            return List.of();
        }
        if (messages.size() > limit) {
            messages = messages.subList(0, limit);
        }
        List<String> ticketIds = messages.stream()
            .map(Message::getTicketId)
            .filter(StringUtils::hasText)
            .toList();
        Map<String, Feedback> latestFeedback = feedbackRepository.findByTicketIdIn(ticketIds).stream()
            .filter(feedback -> feedback.getTicketId() != null)
            .collect(Collectors.toMap(
                Feedback::getTicketId,
                Function.identity(),
                BotRuntimeTicketReadService::pickLatestFeedback
            ));
        return messages.stream()
            .map(message -> new TicketSummaryLookup(
                message.getTicketId(),
                resolveClientTicketNumber(message),
                message.getProblem(),
                message.getBusiness(),
                message.getLocationType(),
                message.getCity(),
                message.getLocationName(),
                Optional.ofNullable(latestFeedback.get(message.getTicketId()))
                    .map(Feedback::getRating)
                    .orElse(null),
                message.getCreatedAt()
            ))
            .toList();
    }

    @Transactional(readOnly = true)
    public Optional<PendingFeedbackRequestLookup> findActiveFeedbackRequest(Long userId, Long channelId) {
        if (userId == null) {
            return Optional.empty();
        }
        OffsetDateTime now = OffsetDateTime.now();
        Optional<PendingFeedbackRequest> request = Optional.empty();
        if (channelId != null) {
            request = pendingFeedbackRequestRepository
                .findFirstByUserIdAndChannel_IdAndExpiresAtAfterOrderByCreatedAtDesc(userId, channelId, now);
        }
        if (request.isPresent() && !hasStoredFeedback(request.get())) {
            return Optional.of(toLookup(request.get()));
        }
        Optional<PendingFeedbackRequest> fallback = pendingFeedbackRequestRepository
            .findFirstByUserIdAndExpiresAtAfterOrderByCreatedAtDesc(userId, now);
        if (fallback.isPresent() && hasStoredFeedback(fallback.get())) {
            return Optional.empty();
        }
        return fallback.map(this::toLookup);
    }

    private List<String> buildIdentities(Long userId, String username) {
        List<String> identities = new ArrayList<>();
        if (userId != null && userId > 0) {
            identities.add(userId.toString());
        }
        if (StringUtils.hasText(username)) {
            identities.add(username.trim());
        }
        return identities;
    }

    private String resolveClientTicketNumber(Message message) {
        if (message == null) {
            return null;
        }
        LocalDate createdDate = message.getCreatedDate();
        OffsetDateTime createdAt = message.getCreatedAt();
        Long messageId = message.getId();
        if (createdDate == null && createdAt != null) {
            createdDate = createdAt.toLocalDate();
        }
        if (createdDate == null || createdAt == null || messageId == null) {
            return message.getTicketId();
        }
        long sequence = messageRepository.countClientSequenceForDay(createdDate, createdAt, messageId);
        if (sequence <= 0) {
            return message.getTicketId();
        }
        return createdDate.format(CLIENT_TICKET_NUMBER_DATE_FORMAT) + "-" + String.format("%03d", sequence);
    }

    private static Feedback pickLatestFeedback(Feedback left, Feedback right) {
        OffsetDateTime leftTime = left.getTimestamp();
        OffsetDateTime rightTime = right.getTimestamp();
        if (leftTime == null && rightTime == null) {
            return right;
        }
        if (leftTime == null) {
            return right;
        }
        if (rightTime == null) {
            return left;
        }
        return leftTime.isAfter(rightTime) ? left : right;
    }

    private boolean hasStoredFeedback(PendingFeedbackRequest request) {
        return request != null
            && StringUtils.hasText(request.getTicketId())
            && feedbackRepository.existsByTicketId(request.getTicketId());
    }

    private PendingFeedbackRequestLookup toLookup(PendingFeedbackRequest request) {
        return new PendingFeedbackRequestLookup(
            request.getId(),
            request.getUserId(),
            request.getChannel() != null ? request.getChannel().getId() : null,
            request.getTicketId(),
            request.getSource(),
            request.getExpiresAt()
        );
    }

    public record ActiveTicketLookup(String ticketId,
                                     String userIdentity,
                                     OffsetDateTime lastSeen) {
    }

    public record TicketLookup(Long userId,
                               String ticketId,
                               String status) {
    }

    public record LastTicketContext(String ticketId,
                                    String business,
                                    String locationType,
                                    String city,
                                    String locationName,
                                    String problem,
                                    OffsetDateTime createdAt,
                                    LocalDate createdDate,
                                    Long messageId) {
    }

    public record RequestNumberLookup(String ticketId,
                                      String requestNumber) {
    }

    public record TicketSummaryLookup(String ticketId,
                                      String requestNumber,
                                      String problem,
                                      String business,
                                      String locationType,
                                      String city,
                                      String locationName,
                                      Integer rating,
                                      OffsetDateTime createdAt) {
    }

    public record PendingFeedbackRequestLookup(Long id,
                                               Long userId,
                                               Long channelId,
                                               String ticketId,
                                               String source,
                                               OffsetDateTime expiresAt) {
    }
}
