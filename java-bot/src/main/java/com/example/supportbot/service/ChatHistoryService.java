package com.example.supportbot.service;

import com.example.supportbot.entity.Channel;
import com.example.supportbot.entity.ChatHistory;
import com.example.supportbot.repository.ChatHistoryRepository;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.telegram.telegrambots.meta.api.objects.Message;

@Service
public class ChatHistoryService {

    private final ChatHistoryRepository historyRepository;

    public ChatHistoryService(ChatHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    @Transactional
    public ChatHistory storeUserMessage(Message telegramMessage, Channel channel, String ticketId, String messageType, String attachmentPath) {
        Long telegramMessageId = telegramMessage.getMessageId() != null ? telegramMessage.getMessageId().longValue() : null;
        Long userId = telegramMessage.getFrom() != null ? telegramMessage.getFrom().getId() : null;
        String text = telegramMessage.getText();
        return storeEntry(userId, telegramMessageId, channel, ticketId, text, messageType, attachmentPath);
    }

    @Transactional
    public ChatHistory storeEntry(Long userId,
                                  Long telegramMessageId,
                                  Channel channel,
                                  String ticketId,
                                  String text,
                                  String messageType,
                                  String attachmentPath) {
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
}
