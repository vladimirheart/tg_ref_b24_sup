package com.example.supportbot.service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

final class InboundProviderIdentity {

    private InboundProviderIdentity() {
    }

    static String resolveProviderEventKey(ConversationTicketCreationCommand command) {
        if (command == null) {
            return null;
        }
        String explicit = trimToNull(command.providerEventKey());
        if (explicit != null) {
            return explicit;
        }
        List<ConversationHistoryEntry> history = command.historyEntries();
        if (history == null) {
            return null;
        }
        for (int index = history.size() - 1; index >= 0; index--) {
            ConversationHistoryEntry entry = history.get(index);
            String providerMessageId = entry != null ? trimToNull(entry.providerMessageId()) : null;
            if (providerMessageId != null) {
                return "message:" + providerMessageId;
            }
        }
        return null;
    }

    static String deterministicId(String namespace, ConversationTicketCreationCommand command) {
        String providerEventKey = resolveProviderEventKey(command);
        if (providerEventKey == null || command == null || command.channel() == null) {
            return null;
        }
        String platform = trimToNull(command.channel().getPlatform());
        if (platform == null) {
            platform = "unknown";
        }
        String source = String.join("|",
            trimToNull(namespace) != null ? namespace.trim() : "event",
            platform.toLowerCase(Locale.ROOT),
            String.valueOf(command.channel().getId()),
            String.valueOf(command.userId()),
            providerEventKey
        );
        return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
