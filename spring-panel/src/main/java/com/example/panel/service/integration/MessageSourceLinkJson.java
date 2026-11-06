package com.example.panel.service.integration;

/** A bounded typed source context, never a URL, root locator or provider send instruction. */
public final class MessageSourceLinkJson {
    private static final com.fasterxml.jackson.databind.ObjectMapper JSON = new com.fasterxml.jackson.databind.ObjectMapper();
    private MessageSourceLinkJson() {}
    public static String write(MessageSourceLink link) {
        if (link == null) return null;
        try { return JSON.writeValueAsString(link); }
        catch (com.fasterxml.jackson.core.JsonProcessingException invalid) { throw new IllegalArgumentException("Invalid source link", invalid); }
    }
    public static MessageSourceLink read(String json) {
        if (json == null || json.length() > 4096) return null;
        try { return JSON.readValue(json, MessageSourceLink.class); }
        catch (com.fasterxml.jackson.core.JsonProcessingException invalid) { return null; }
    }
}
