package com.example.supportbot.service;

import com.example.supportbot.entity.Channel;
import com.example.supportbot.entity.PendingFeedbackRequest;
import com.example.supportbot.entity.Ticket;
import com.example.supportbot.entity.TicketActive;
import com.example.supportbot.entity.TicketId;
import com.example.supportbot.entity.TicketMessage;
import com.example.supportbot.entity.TicketSpan;
import com.example.supportbot.repository.PendingFeedbackRequestRepository;
import com.example.supportbot.repository.TicketActiveRepository;
import com.example.supportbot.repository.TicketMessageRepository;
import com.example.supportbot.repository.TicketRepository;
import com.example.supportbot.repository.TicketSpanRepository;
import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TicketService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final TicketRepository ticketRepository;
    private final TicketMessageRepository messageRepository;
    private final PendingFeedbackRequestRepository pendingFeedbackRequestRepository;
    private final TicketSpanRepository ticketSpanRepository;
    private final TicketActiveRepository ticketActiveRepository;
    private final ChatHistoryService chatHistoryService;

    public TicketService(TicketRepository ticketRepository,
                         TicketMessageRepository messageRepository,
                         PendingFeedbackRequestRepository pendingFeedbackRequestRepository,
                         TicketSpanRepository ticketSpanRepository,
                         TicketActiveRepository ticketActiveRepository,
                         ChatHistoryService chatHistoryService) {
        this.ticketRepository = ticketRepository;
        this.messageRepository = messageRepository;
        this.pendingFeedbackRequestRepository = pendingFeedbackRequestRepository;
        this.ticketSpanRepository = ticketSpanRepository;
        this.ticketActiveRepository = ticketActiveRepository;
        this.chatHistoryService = chatHistoryService;
    }

    @Transactional
    public TicketCreationResult createTicket(long userId,
                                             String username,
                                             Map<String, String> answers,
                                             Channel channel) {
        OffsetDateTime now = OffsetDateTime.now();
        String ticketId = UUID.randomUUID().toString();

        TicketMessage message = new TicketMessage();
        message.setId(now.toInstant().toEpochMilli());
        message.setUserId(userId);
        message.setBusiness(answers.getOrDefault("business", ""));
        message.setLocationType(answers.getOrDefault("location_type", ""));
        message.setCity(answers.getOrDefault("city", ""));
        message.setLocationName(answers.getOrDefault("location_name", ""));
        message.setProblem(answers.getOrDefault("problem", ""));
        message.setCreatedAt(now);
        message.setCreatedDate(now.toLocalDate());
        message.setCreatedTime(TIME_FORMATTER.format(now));
        message.setUsername(username);
        message.setTicketId(ticketId);
        message.setChannel(channel);
        messageRepository.save(message);

        Ticket ticket = new Ticket();
        ticket.setId(new TicketId(userId, ticketId));
        ticket.setGroupMessageId(message.getId());
        ticket.setStatus("open");
        ticket.setChannel(channel);
        ticket.setReopenCount(0);
        ticket.setClosedCount(0);
        ticket.setWorkTimeTotalSec(0L);
        ticket.setLastReopenAt(now);
        ticketRepository.save(ticket);

        TicketSpan span = new TicketSpan();
        span.setTicketId(ticketId);
        span.setSpanNumber(1);
        span.setStartedAt(now);
        ticketSpanRepository.save(span);

        TicketActive active = new TicketActive();
        active.setTicketId(ticketId);
        active.setUser(username != null ? username : Long.toString(userId));
        active.setLastSeen(now);
        ticketActiveRepository.save(active);

        return new TicketCreationResult(ticketId, message.getId(), "open");
    }

    @Transactional(readOnly = true)
    public Optional<TicketMessage> findLastMessage(long userId) {
        return messageRepository.findTopByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public Optional<TicketActive> findActiveTicketForUser(Long userId, String username) {
        List<String> identities = new ArrayList<>();
        if (userId != null && userId > 0) {
            identities.add(userId.toString());
        }
        if (username != null && !username.isBlank()) {
            identities.add(username);
        }
        if (identities.isEmpty()) {
            return Optional.empty();
        }
        List<TicketActive> active = ticketActiveRepository.findByUserInOrderByLastSeenDesc(identities);
        return active.isEmpty() ? Optional.empty() : Optional.of(active.get(0));
    }

    @Transactional(readOnly = true)
    public Optional<TicketWithUser> findByTicketId(String ticketId) {
        return ticketRepository.findByIdTicketId(ticketId)
                .map(ticket -> new TicketWithUser(ticket.getUserId(), ticket.getTicketId(), ticket.getStatus()));
    }

    @Transactional
    public Optional<Ticket> reopenTicket(String ticketId) {
        Optional<Ticket> ticketOpt = ticketRepository.findByIdTicketId(ticketId);
        if (ticketOpt.isEmpty()) {
            return Optional.empty();
        }
        Ticket ticket = ticketOpt.get();
        OffsetDateTime now = OffsetDateTime.now();
        ticket.setStatus("open");
        ticket.setReopenCount(Optional.ofNullable(ticket.getReopenCount()).orElse(0) + 1);
        ticket.setLastReopenAt(now);
        ticketRepository.save(ticket);

        int nextSpan = ticketSpanRepository.findTopByTicketIdOrderBySpanNumberDesc(ticketId)
                .map(span -> span.getSpanNumber() + 1)
                .orElse(1);
        TicketSpan span = new TicketSpan();
        span.setTicketId(ticketId);
        span.setSpanNumber(nextSpan);
        span.setStartedAt(now);
        ticketSpanRepository.save(span);

        TicketActive active = new TicketActive();
        active.setTicketId(ticketId);
        active.setUser(ticket.getUserId() != null ? ticket.getUserId().toString() : "");
        active.setLastSeen(now);
        ticketActiveRepository.save(active);

        chatHistoryService.storeSystemEvent(ticket.getUserId(), ticketId, ticket.getChannel(),
                "Заявка переоткрыта оператором.");

        return Optional.of(ticket);
    }

    @Transactional
    public boolean closeTicket(String ticketId, String resolvedBy, String source) {
        Optional<Ticket> ticketOpt = ticketRepository.findByIdTicketId(ticketId);
        if (ticketOpt.isEmpty()) {
            return false;
        }

        Ticket ticket = ticketOpt.get();
        OffsetDateTime now = OffsetDateTime.now();
        ticket.setStatus("closed");
        ticket.setResolvedAt(now);
        ticket.setResolvedBy(resolvedBy);
        ticket.setClosedCount(Optional.ofNullable(ticket.getClosedCount()).orElse(0) + 1);
        Long totalWork = Optional.ofNullable(ticket.getWorkTimeTotalSec()).orElse(0L);
        long spanSeconds = closeOpenSpan(ticketId, now);
        ticket.setWorkTimeTotalSec(totalWork + spanSeconds);
        ticketRepository.save(ticket);

        ticketActiveRepository.findById(ticketId).ifPresent(ticketActiveRepository::delete);

        chatHistoryService.storeSystemEvent(ticket.getUserId(), ticketId, ticket.getChannel(),
                "Заявка закрыта. Причина: " + Optional.ofNullable(source).orElse("оператор"));

        pendingFeedbackRequestRepository.findFirstByTicketIdOrderByCreatedAtDesc(ticketId)
                .ifPresentOrElse(request -> {
                    request.setExpiresAt(now.plusDays(1));
                    if (source != null && !source.isBlank()) {
                        request.setSource(source);
                    }
                    pendingFeedbackRequestRepository.save(request);
                }, () -> createPendingFeedback(ticket, source, now));
        return true;
    }

    @Transactional
    public int closeInactiveTickets(Duration inactivityLimit) {
        OffsetDateTime threshold = OffsetDateTime.now().minus(inactivityLimit);
        int closed = 0;
        for (TicketActive active : ticketActiveRepository.findAll()) {
            if (active.getLastSeen() != null && active.getLastSeen().isBefore(threshold)) {
                if (closeTicket(active.getTicketId(), "auto_close", "inactivity")) {
                    closed++;
                }
            }
        }
        return closed;
    }

    @Transactional
    public void registerActivity(String ticketId, String username) {
        OffsetDateTime now = OffsetDateTime.now();
        TicketActive active = ticketActiveRepository.findById(ticketId).orElseGet(() -> {
            TicketActive placeholder = new TicketActive();
            placeholder.setTicketId(ticketId);
            return placeholder;
        });
        active.setLastSeen(now);
        if (active.getUser() == null || active.getUser().isBlank()) {
            active.setUser(username);
        }
        ticketActiveRepository.save(active);
    }

    @Transactional
    public void ensureFeedbackRequest(String ticketId, Long userId, Channel channel, String source) {
        OffsetDateTime now = OffsetDateTime.now();
        boolean markAsSent = "user_prompt".equalsIgnoreCase(source);
        pendingFeedbackRequestRepository.findFirstByTicketIdOrderByCreatedAtDesc(ticketId)
                .ifPresentOrElse(request -> {
                    request.setExpiresAt(now.plusDays(1));
                    request.setSource(source);
                    if (markAsSent && request.getSentAt() == null) {
                        request.setSentAt(now);
                    }
                    pendingFeedbackRequestRepository.save(request);
                }, () -> {
                    PendingFeedbackRequest request = new PendingFeedbackRequest();
                    request.setUserId(userId);
                    request.setChannel(channel);
                    request.setTicketId(ticketId);
                    request.setSource(source);
                    request.setCreatedAt(now);
                    request.setExpiresAt(now.plusDays(1));
                    if (markAsSent) {
                        request.setSentAt(now);
                    }
                    pendingFeedbackRequestRepository.save(request);
                });
    }

    private long closeOpenSpan(String ticketId, OffsetDateTime now) {
        return ticketSpanRepository.findFirstByTicketIdAndEndedAtIsNullOrderBySpanNumberDesc(ticketId)
                .map(span -> {
                    span.setEndedAt(now);
                    span.setDurationSeconds((int) Duration.between(span.getStartedAt(), now).getSeconds());
                    ticketSpanRepository.save(span);
                    return span.getDurationSeconds().longValue();
                })
                .orElse(0L);
    }

    private void createPendingFeedback(Ticket ticket, String source, OffsetDateTime now) {
        PendingFeedbackRequest request = new PendingFeedbackRequest();
        request.setUserId(ticket.getUserId());
        request.setChannel(ticket.getChannel());
        request.setTicketId(ticket.getTicketId());
        request.setSource(source);
        request.setCreatedAt(now);
        request.setExpiresAt(now.plusDays(1));
        pendingFeedbackRequestRepository.save(request);
    }

    public record TicketCreationResult(String ticketId, Long groupMessageId, String status) {
    }

    public record TicketWithUser(Long userId, String ticketId, String status) {
    }
}
