package com.example.supportbot.service;

public interface OutboundMessenger {

    String platform();

    boolean sendToUser(Long userId, String text);

    boolean sendToSupportChat(String supportChatId, String text);
}