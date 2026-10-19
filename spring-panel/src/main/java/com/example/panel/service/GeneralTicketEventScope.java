package com.example.panel.service;

/** Separate GENERAL identity; never makes UiEventTicketScope.isValid accept GENERAL. */
public record GeneralTicketEventScope(Long ticketUserId, String ticketId, String ticketPublicId) {
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isValid() {
        return ticketUserId != null && ticketUserId != 0 && ticketId != null && !ticketId.isBlank()
            && ticketPublicId != null && ticketPublicId.matches("[0-9a-f]{32}");
    }
    public static GeneralTicketEventScope from(UiEventTicketScope scope) {
        if (scope == null || !"GENERAL".equals(scope.scopeKind()) || scope.businessId() != null) return null;
        var general = new GeneralTicketEventScope(scope.ticketUserId(), scope.ticketId(), scope.ticketPublicId());
        return general.isValid() ? general : null;
    }
    @Override public String toString() { return "GeneralTicketEventScope[redacted]"; }
}
