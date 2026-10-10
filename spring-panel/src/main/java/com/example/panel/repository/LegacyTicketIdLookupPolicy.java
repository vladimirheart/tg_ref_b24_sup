package com.example.panel.repository;

import com.example.panel.entity.Ticket;
import java.util.List;
import java.util.Optional;

final class LegacyTicketIdLookupPolicy {

    private LegacyTicketIdLookupPolicy() {
    }

    static Optional<Ticket> onlyUnique(List<Ticket> candidates) {
        if (candidates == null || candidates.size() != 1) {
            return Optional.empty();
        }
        return Optional.ofNullable(candidates.get(0));
    }
}
