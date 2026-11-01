package com.example.supportbot.service;

public interface OutboundMessenger {

    String platform();

    /** Unbound/older providers must fail closed, not inherit the event's channel. */
    default OutboundSenderIdentity senderIdentity() { return null; }

    boolean sendToUser(Long userId, String text);

    boolean sendToSupportChat(String supportChatId, String text);
}
