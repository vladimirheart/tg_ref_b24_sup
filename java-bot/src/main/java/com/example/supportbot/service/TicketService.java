package com.example.supportbot.service;

import com.example.supportbot.entity.Channel;
import com.example.supportbot.entity.PendingFeedbackRequest;
import com.example.supportbot.entity.Ticket;
import com.example.supportbot.entity.TicketId;
import com.example.supportbot.entity.TicketMessage;
import com.example.supportbot.repository.PendingFeedbackRequestRepository;
import com.example.supportbot.repository.TicketMessageRepository;
import com.example.supportbot.repository.TicketRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.User;

@Service
public class TicketService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final TicketRepository ticketRepository;
    private final TicketMessageRepository messageRepository;
    private final PendingFeedbackRequestRepository pendingFeedbackRequestRepository;

    public TicketService(TicketRepository ticketRepository,
                         TicketMessageRepository messageRepository,
                         PendingFeedbackRequestRepository pendingFeedbackRequestRepository) {
        this.ticketRepository = ticketRepository;
        this.messageRepository = messageRepository;
        this.pendingFeedbackRequestRepository = pendingFeedbackRequestRepository;
    }

    @Transactional
    public TicketCreationResult createTicket(long userId,
                                             User telegramUser,
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
        message.setUsername(telegramUser != null ? telegramUser.getUserName() : null);
        message.setTicketId(ticketId);
        message.setChannel(channel);
        messageRepository.save(message);

        Ticket ticket = new Ticket();
        ticket.setId(new TicketId(userId, ticketId));
        ticket.setGroupMessageId(message.getId());
        ticket.setStatus("pending");
        ticket.setChannel(channel);
        ticketRepository.save(ticket);

        PendingFeedbackRequest feedbackRequest = new PendingFeedbackRequest();
        feedbackRequest.setUserId(userId);
        feedbackRequest.setChannel(channel);
        feedbackRequest.setTicketId(ticketId);
        feedbackRequest.setSource("operator_close");
        feedbackRequest.setCreatedAt(now);
        feedbackRequest.setExpiresAt(now.plusDays(1));
        feedbackRequest.setSentAt(now);
        pendingFeedbackRequestRepository.save(feedbackRequest);

        return new TicketCreationResult(ticketId, message.getId());
    }

    @Transactional(readOnly = true)
    public Optional<TicketMessage> findLastMessage(long userId) {
        return messageRepository.findTopByUserIdOrderByCreatedAtDesc(userId);
    }

    public record TicketCreationResult(String ticketId, Long groupMessageId) {
    }
}
