package com.example.supportbot.service;

public interface OutboundMessenger {

    String platform();

    /** Unbound/older providers must fail closed, not inherit the event's channel. */
    default OutboundSenderIdentity senderIdentity() { return null; }

    boolean sendToUser(Long userId, String text);

    /** Compatibility never invents a provider ID and never retries a failed provider call. */
    default ProviderSendResult sendToUserWithReceipt(Long userId, String text) {
        return ProviderSendResult.legacy(sendToUser(userId,text));
    }

    boolean sendToSupportChat(String supportChatId, String text);
}
