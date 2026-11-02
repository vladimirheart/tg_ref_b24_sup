package com.example.supportbot.service;

/** Provider acceptance is not a client read receipt. Older adapters can report success without an ID. */
public record ProviderSendResult(boolean accepted, String messageId) {
    public ProviderSendResult {
        if (messageId != null && (!accepted || !messageId.matches("[A-Za-z0-9._:-]{1,256}"))) {
            throw new IllegalArgumentException("Invalid provider message receipt.");
        }
    }
    public static ProviderSendResult unknown() { return new ProviderSendResult(false,null); }
    public static ProviderSendResult legacy(boolean accepted) { return new ProviderSendResult(accepted,null); }
    public static ProviderSendResult accepted(String id) { return new ProviderSendResult(true,id); }
    @Override public String toString() { return "ProviderSendResult[accepted="+accepted+", receipt="+(messageId==null?"absent":"<redacted>")+"]"; }
}
