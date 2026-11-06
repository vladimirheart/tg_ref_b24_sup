package com.example.supportbot.service;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Set;

/** Original provider identity is metadata only; it never selects the owning client, ticket or channel. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record MessageSourceLink(String type, String providerMessageId, String authorUserId, String authorName) {
    public static final String BATCH_TYPE = "selected_media_batch.max_linked.v1";
    public static final String HISTORY_PREFIX = "max_linked.v1:";
    private static final Set<String> MEDIA = Set.of("document", "photo", "audio", "video");
    public MessageSourceLink {
        if (!Set.of("forward", "reply").contains(type == null ? "" : type)
                || providerMessageId == null || providerMessageId.isBlank() || providerMessageId.length() > 512
                || providerMessageId.codePoints().anyMatch(c -> Character.isISOControl(c) || Character.isWhitespace(c))
                || authorName != null && (authorName.isBlank() || authorName.length() > 255 || authorName.codePoints().anyMatch(Character::isISOControl)))
            throw new IllegalArgumentException("Invalid MAX source link");
        if (authorUserId != null) {
            if (!authorUserId.matches("[1-9][0-9]{0,18}")) throw new IllegalArgumentException("Invalid source author identity");
            try { Long.parseLong(authorUserId); } catch (NumberFormatException invalid) { throw new IllegalArgumentException("Invalid source author identity"); }
        }
    }
    public static boolean batchType(String messageType, MessageSourceLink link) {
        return (link == null ? "selected_media_batch.v1" : BATCH_TYPE).equals(messageType);
    }
    public static String mediaType(String platform, String wireType, MessageSourceLink link) {
        if (link == null) {
            if (wireType != null && wireType.startsWith(HISTORY_PREFIX)) throw new IllegalArgumentException("Source link proof is missing");
            return wireType;
        }
        if (!"max".equals(platform) || wireType == null || !wireType.startsWith(HISTORY_PREFIX)
                || !MEDIA.contains(wireType.substring(HISTORY_PREFIX.length()))) throw new IllegalArgumentException("Source link requires the MAX linked media protocol");
        return wireType.substring(HISTORY_PREFIX.length());
    }
}
