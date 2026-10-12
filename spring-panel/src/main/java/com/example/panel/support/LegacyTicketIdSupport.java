package com.example.panel.support;

import org.springframework.util.StringUtils;

/** Opaque legacy ID text handling only; this does not resolve a ticket or grant access. */
public final class LegacyTicketIdSupport {
    private LegacyTicketIdSupport() {
    }

    public static String exactTicketIdOrNull(String ticketId) {
        return StringUtils.hasText(ticketId) ? ticketId : null;
    }
}
