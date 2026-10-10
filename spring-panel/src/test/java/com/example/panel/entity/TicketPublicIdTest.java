package com.example.panel.entity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TicketPublicIdTest {

    @Test
    void assignsStableLowercaseOpaquePublicIdWhenMissing() {
        Ticket ticket = new Ticket();

        ticket.assignTicketPublicIdIfMissing();
        String assigned = ticket.getTicketPublicId();
        ticket.assignTicketPublicIdIfMissing();

        assertThat(assigned).matches("[0-9a-f]{32}");
        assertThat(ticket.getTicketPublicId()).isEqualTo(assigned);
    }
}
