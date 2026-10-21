package com.example.supportbot.service;

import java.time.OffsetDateTime;

public record InboundClientMessageEvent(String eventId,
                                        String eventKind,
                                        String platform,
                                        Long channelId,
                                        String ticketId,
                                        Long userId,
                                        String userIdentity,
                                        String username,
                                        String clientName,
                                        String text,
                                        String messageType,
                                        String attachmentPath,
                                        String attachmentName,
                                        String providerMessageId,
                                        String replyToProviderMessageId,
                                        String forwardedFrom,
                                        OffsetDateTime occurredAt,
                                        java.util.List<MediaItem> mediaBatch) {
    public static final String MEDIA_BATCH_KIND="client_message.selected_media_batch.v1";
    public record MediaItem(String messageType,String attachmentPath,String attachmentName) {}
    public InboundClientMessageEvent {
        if(mediaBatch!=null) mediaBatch=java.util.List.copyOf(mediaBatch);
    }
    public InboundClientMessageEvent(String eventId,String eventKind,String platform,Long channelId,String ticketId,Long userId,
            String userIdentity,String username,String clientName,String text,String messageType,String attachmentPath,
            String attachmentName,String providerMessageId,String replyToProviderMessageId,String forwardedFrom,OffsetDateTime occurredAt) {
        this(eventId,eventKind,platform,channelId,ticketId,userId,userIdentity,username,clientName,text,messageType,attachmentPath,
            attachmentName,providerMessageId,replyToProviderMessageId,forwardedFrom,occurredAt,null);
    }
    public static String mediaBatchId(long channelId,long userId,String ticketId,String providerId) {
        // Length-prefix the opaque ticket ID: delimiters inside it cannot alias another tuple.
        String source=MEDIA_BATCH_KIND+":"+channelId+":"+userId+":"+ticketId.length()+":"+ticketId+":"+providerId;
        return java.util.UUID.nameUUIDFromBytes(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)).toString();
    }
}
