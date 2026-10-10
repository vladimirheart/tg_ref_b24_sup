package com.example.panel.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.panel.entity.Ticket;
import java.util.List;
import org.junit.jupiter.api.Test;

class TicketRepositoryLegacyLookupTest {

    @Test
    void resolvesBareLegacyIdOnlyWhenThereIsExactlyOneCandidate() {
        Ticket uniqueTicket = new Ticket();
        Ticket anotherTicket = new Ticket();

        assertThat(LegacyTicketIdLookupPolicy.onlyUnique(List.of())).isEmpty();
        assertThat(LegacyTicketIdLookupPolicy.onlyUnique(List.of(uniqueTicket))).containsSame(uniqueTicket);
        assertThat(LegacyTicketIdLookupPolicy.onlyUnique(List.of(uniqueTicket, anotherTicket))).isEmpty();
    }
}
