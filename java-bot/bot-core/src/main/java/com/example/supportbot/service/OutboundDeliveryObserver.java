package com.example.supportbot.service;

/** Durable, attempt-bound observation around exactly one provider call. */
public interface OutboundDeliveryObserver {
    void beforeProvider(OutboundSenderIdentity sender);
    void accepted(OutboundSenderIdentity sender, Long userId, ProviderSendResult result);
}
