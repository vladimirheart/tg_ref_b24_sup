package com.example.panel.service;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.util.StringUtils;

/** Frozen canonical owner of a ticket event; kept in the internal fanout envelope only. */
public record UiEventTicketScope(
    Long ticketUserId,
    String ticketId,
    String ticketPublicId,
    String scopeKind,
    Long businessId
) {
    @JsonIgnore
    public boolean isValid() {
        return ticketUserId != null && StringUtils.hasText(ticketId)
            && ticketPublicId != null && ticketPublicId.matches("[0-9a-f]{32}")
            && (("BUSINESS".equals(scopeKind) && businessId != null && businessId > 0)
                || ("UNRESOLVED".equals(scopeKind) && businessId == null));
    }
}
