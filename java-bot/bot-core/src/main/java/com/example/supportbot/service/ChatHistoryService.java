package com.example.supportbot.service;

import com.example.supportbot.entity.Channel;
import com.example.supportbot.entity.ChatHistory;
import com.example.supportbot.repository.ChatHistoryRepository;
import java.time.OffsetDateTime;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatHistoryService {

    private final ChatHistoryRepository historyRepository;
    private final JdbcTemplate jdbcTemplate;
    private final UiEventOutboxService uiEventOutboxService;

    public ChatHistoryService(ChatHistoryRepository historyRepository,
                              JdbcTemplate jdbcTemplate,
                              UiEventOutboxService uiEventOutboxService) {
        this.historyRepository = historyRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.uiEventOutboxService = uiEventOutboxService;
        ensureColumns();
    }

    private void ensureColumns() {
        try {
            jdbcTemplate.execute("ALTER TABLE chat_history ADD COLUMN original_message TEXT");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("ALTER TABLE chat_history ADD COLUMN forwarded_from TEXT");
        } catch (Exception ignored) {
        }
        try {
            jdbcTemplate.execute("ALTER TABLE chat_history ADD COLUMN file_name TEXT");
        } catch (Exception ignored) {
        }
    }

    @Transactional
    public ChatHistory storeUserMessage(Long userId,
                                        Long telegramMessageId,
                                        String text,
                                        Channel channel,
                                        String ticketId,
                                        String messageType,
                                        String attachmentPath,
                                        Long replyToTelegramId,
                                        String forwardedFrom) {
        return storeUserMessage(userId, telegramMessageId, text, channel, ticketId, messageType, attachmentPath, null, replyToTelegramId, forwardedFrom);
    }

    @Transactional
    public ChatHistory storeUserMessage(Long userId,
                                        Long telegramMessageId,
                                        String text,
                                        Channel channel,
                                        String ticketId,
                                        String messageType,
                                        String attachmentPath,
                                        String attachmentName,
                                        Long replyToTelegramId,
                                        String forwardedFrom) {
        return storeEntry(userId, telegramMessageId, channel, ticketId, text, messageType, attachmentPath, attachmentName, replyToTelegramId, forwardedFrom);
    }

    @Transactional
    public ChatHistory storeEntry(Long userId,
                                  Long telegramMessageId,
                                  Channel channel,
                                  String ticketId,
                                  String text,
                                  String messageType,
                                  String attachmentPath,
                                  Long replyToTelegramId,
                                  String forwardedFrom) {
        return storeEntry(userId, telegramMessageId, channel, ticketId, text, messageType, attachmentPath, null, replyToTelegramId, forwardedFrom);
    }

    @Transactional
    public ChatHistory storeEntry(Long userId,
                                  Long telegramMessageId,
                                  Channel channel,
                                  String ticketId,
                                  String text,
                                  String messageType,
                                  String attachmentPath,
                                  String attachmentName,
                                  Long replyToTelegramId,
                                  String forwardedFrom) {
        ChatHistory history = new ChatHistory();
        history.setUserId(userId);
        history.setSender("client");
        history.setMessage(text);
        history.setTimestamp(OffsetDateTime.now().toString());
        history.setTicketId(ticketId);
        history.setMessageType(messageType);
        history.setAttachment(attachmentPath);
        history.setFileName(attachmentName);
        history.setChannel(channel);
        history.setTelegramMessageId(telegramMessageId);
        history.setReplyToTelegramId(replyToTelegramId);
        history.setForwardedFrom(forwardedFrom);
        ChatHistory saved = historyRepository.save(history);
        uiEventOutboxService.publishClientMessage(ticketId, channel, text, messageType, attachmentPath);
        return saved;
    }

    @Transactional
    public ChatHistory storeOperatorMessage(Long userId,
                                            String ticketId,
                                            String text,
                                            Channel channel,
                                            Long telegramMessageId,
                                            Long replyToTelegramId) {
        ChatHistory history = new ChatHistory();
        history.setUserId(userId);
        history.setSender("operator");
        history.setMessage(text);
        history.setTimestamp(OffsetDateTime.now().toString());
        history.setTicketId(ticketId);
        history.setMessageType("operator_message");
        history.setChannel(channel);
        history.setTelegramMessageId(telegramMessageId);
        history.setReplyToTelegramId(replyToTelegramId);
        return historyRepository.save(history);
    }

    @Transactional
    public boolean markClientMessageEdited(Long channelId, Long telegramMessageId, String newText) {
        int updated = jdbcTemplate.update("""
                UPDATE chat_history
                   SET original_message = COALESCE(original_message, message),
                       message = ?,
                       edited_at = CURRENT_TIMESTAMP
                 WHERE channel_id = ?
                   AND tg_message_id = ?
                   AND sender = 'client'
                """, newText, channelId, telegramMessageId);
        if (updated > 0) {
            uiEventOutboxService.publishClientMessageEdited(resolveTicketId(channelId, telegramMessageId), channelId, newText);
        }
        return updated > 0;
    }

    @Transactional
    public ChatHistory storeSystemEvent(Long userId, String ticketId, Channel channel, String text) {
        ChatHistory history = new ChatHistory();
        history.setUserId(userId);
        history.setSender("system");
        history.setMessage(text);
        history.setTimestamp(OffsetDateTime.now().toString());
        history.setTicketId(ticketId);
        history.setMessageType("system_event");
        history.setChannel(channel);
        return historyRepository.save(history);
    }

    private String resolveTicketId(Long channelId, Long telegramMessageId) {
        if (channelId == null || telegramMessageId == null) {
            return null;
        }
        return jdbcTemplate.query("""
                SELECT ticket_id
                  FROM chat_history
                 WHERE channel_id = ?
                   AND tg_message_id = ?
                 ORDER BY id DESC
                 LIMIT 1
                """, rs -> rs.next() ? rs.getString("ticket_id") : null, channelId, telegramMessageId);
    }
}
