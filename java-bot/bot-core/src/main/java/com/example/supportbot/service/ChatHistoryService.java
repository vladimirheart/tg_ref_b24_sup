package com.example.supportbot.service;

import com.example.supportbot.entity.Channel;
import com.example.supportbot.entity.ChatHistory;
import com.example.supportbot.repository.ChatHistoryRepository;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatHistoryService {

    private final ChatHistoryRepository historyRepository;

    public ChatHistoryService(ChatHistoryRepository historyRepository) {
        this.historyRepository = historyRepository;
    }

    @Transactional
    public ChatHistory storeUserMessage(Long userId,
                                        Long telegramMessageId,
                                        String text,
                                        Channel channel,
                                        String ticketId,
                                        String messageType,
                                        String attachmentPath) {
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
