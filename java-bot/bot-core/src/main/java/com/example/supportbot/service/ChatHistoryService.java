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

    public ChatHistoryService(ChatHistoryRepository historyRepository, JdbcTemplate jdbcTemplate) {
        this.historyRepository = historyRepository;
        this.jdbcTemplate = jdbcTemplate;
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
        return storeEntry(userId, telegramMessageId, channel, ticketId, text, messageType, attachmentPath, replyToTelegramId, forwardedFrom);
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
        ChatHistory history = new ChatHistory();
        history.setUserId(userId);
        history.setSender("client");
        history.setMessage(text);
        history.setTimestamp(OffsetDateTime.now().toString());
        history.setTicketId(ticketId);
        history.setMessageType(messageType);
        history.setAttachment(attachmentPath);
        history.setChannel(channel);
        history.setTelegramMessageId(telegramMessageId);
        history.setReplyToTelegramId(replyToTelegramId);
        history.setForwardedFrom(forwardedFrom);
        return historyRepository.save(history);
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
}
