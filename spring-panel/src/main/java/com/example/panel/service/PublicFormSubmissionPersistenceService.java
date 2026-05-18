package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.entity.ChatHistory;
import com.example.panel.entity.Message;
import com.example.panel.entity.Ticket;
import com.example.panel.entity.TicketId;
import com.example.panel.entity.WebFormSession;
import com.example.panel.model.publicform.PublicFormSessionDto;
import com.example.panel.model.publicform.PublicFormSubmission;
import com.example.panel.repository.ChatHistoryRepository;
import com.example.panel.repository.MessageRepository;
import com.example.panel.repository.TicketRepository;
import com.example.panel.repository.WebFormSessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class PublicFormSubmissionPersistenceService {
    private static final Logger log = LoggerFactory.getLogger(PublicFormSubmissionPersistenceService.class);

    private final WebFormSessionRepository sessionRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final TicketRepository ticketRepository;
    private final MessageRepository messageRepository;
    private final ObjectMapper objectMapper;
    private final DialogAuditService dialogAuditService;
    private final AlertQueueService alertQueueService;
    private final AtomicLong syntheticMessageId = new AtomicLong(System.currentTimeMillis());

    public PublicFormSubmissionPersistenceService(WebFormSessionRepository sessionRepository,
                                                  ChatHistoryRepository chatHistoryRepository,
                                                  TicketRepository ticketRepository,
                                                  MessageRepository messageRepository,
                                                  ObjectMapper objectMapper,
                                                  DialogAuditService dialogAuditService,
                                                  AlertQueueService alertQueueService) {
        this.sessionRepository = sessionRepository;
        this.chatHistoryRepository = chatHistoryRepository;
        this.ticketRepository = ticketRepository;
        this.messageRepository = messageRepository;
        this.objectMapper = objectMapper;
        this.dialogAuditService = dialogAuditService;
        this.alertQueueService = alertQueueService;
    }

    public PublicFormSessionDto persistSubmission(Channel channel,
                                                  PublicFormSubmissionPolicyService.PreparedSubmission preparedSubmission,
                                                  String requesterKey) {
        PublicFormSubmission normalizedSubmission = preparedSubmission.submission();
        Map<String, String> answers = preparedSubmission.answers();
        String combinedMessage = preparedSubmission.combinedMessage();

        WebFormSession session = new WebFormSession();
        session.setChannel(channel);
        session.setToken(generateToken());
        session.setTicketId(generateTicketId());
        session.setUserId(generateSyntheticUserId(channel.getId(), requesterKey));
        session.setAnswersJson(writeJson(answers));
        session.setClientName(preparedSubmission.clientName());
        session.setClientContact(trim(normalizedSubmission.clientContact()));
        session.setUsername(StringUtils.hasText(normalizedSubmission.username()) ? normalizedSubmission.username().trim() : "web_form");

        OffsetDateTime now = OffsetDateTime.now();
        session.setCreatedAt(now);
        session.setLastActiveAt(now);

        createTicketProjection(channel, session, normalizedSubmission, answers, now);
        WebFormSession saved = sessionRepository.save(session);

        ChatHistory history = new ChatHistory();
        history.setChannel(channel);
        history.setTicketId(saved.getTicketId());
        history.setSender("user");
        history.setMessage(combinedMessage);
        history.setTimestamp(now);
        history.setMessageType("text");
        chatHistoryRepository.save(history);

        PublicFormSessionDto result = new PublicFormSessionDto(
                saved.getToken(),
                saved.getTicketId(),
                channel.getId(),
                channel.getPublicId(),
                saved.getClientName(),
                saved.getClientContact(),
                saved.getUsername(),
                saved.getCreatedAt()
        );

        dialogAuditService.logDialogActionAudit(
                saved.getTicketId(),
                saved.getUsername(),
                "public_form_submit",
                "success",
                "channel=" + channel.getId() + ", source=web_form"
        );
        alertQueueService.notifyQueueForNewPublicAppeal(channel, saved.getTicketId(), combinedMessage);
        return result;
    }

    private void createTicketProjection(Channel channel,
                                        WebFormSession session,
                                        PublicFormSubmission submission,
                                        Map<String, String> answers,
                                        OffsetDateTime now) {
        Ticket ticket = new Ticket();
        TicketId ticketId = new TicketId();
        ticketId.setTicketId(session.getTicketId());
        ticketId.setUserId(session.getUserId());
        ticket.setId(ticketId);
        ticket.setGroupMessageId(nextSyntheticMessageId());
        ticket.setStatus("open");
        ticket.setChannel(channel);
        ticket.setReopenCount(0);
        ticket.setClosedCount(0);
        ticket.setWorkTimeTotalSec(0L);
        ticketRepository.save(ticket);

        Message message = new Message();
        message.setId(ticket.getGroupMessageId());
        message.setUserId(session.getUserId());
        message.setBusiness(firstNonBlankAnswer(answers, "business", "department"));
        message.setCity(firstNonBlankAnswer(answers, "city"));
        message.setLocationName(firstNonBlankAnswer(answers, "location", "location_name", "office"));
        message.setProblem(trim(submission.message()));
        message.setCreatedAt(now);
        message.setUsername(session.getUsername());
        message.setTicketId(session.getTicketId());
        message.setCreatedDate(now.toLocalDate());
        message.setCreatedTime(now.toLocalTime().withNano(0).toString());
        message.setClientName(session.getClientName());
        message.setChannel(channel);
        message.setUpdatedAt(now);
        message.setUpdatedBy("public_form");
        messageRepository.save(message);
    }

    private Long nextSyntheticMessageId() {
        return syntheticMessageId.incrementAndGet();
    }

    private long generateSyntheticUserId(Long channelId, String requesterKey) {
        String seed = (channelId == null ? "0" : channelId.toString()) + "|"
                + (requesterKey == null ? "anon" : requesterKey.trim());
        return 900_000_000L + Integer.toUnsignedLong(seed.hashCode());
    }

    private String firstNonBlankAnswer(Map<String, String> answers, String... keys) {
        for (String key : keys) {
            String value = answers.get(key);
            if (StringUtils.hasText(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private String writeJson(Map<String, String> answers) {
        try {
            return objectMapper.writeValueAsString(answers);
        } catch (Exception ex) {
            log.warn("Failed to serialize answers: {}", ex.getMessage());
            return "{}";
        }
    }

    private String generateToken() {
        return UUID.randomUUID().toString();
    }

    private String generateTicketId() {
        return "web-" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 8).toLowerCase(Locale.ROOT);
    }

    private String trim(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
