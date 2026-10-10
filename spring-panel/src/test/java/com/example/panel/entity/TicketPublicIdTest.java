package com.example.panel.entity;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.persistence.Column;
import java.lang.reflect.Field;
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

    @Test
    void exposesNullableCanonicalBusinessOwnerWithoutAssigningItImplicitly() throws Exception {
        Ticket ticket = new Ticket();
        Field businessId = Ticket.class.getDeclaredField("businessId");
        Column column = businessId.getAnnotation(Column.class);

        assertThat(ticket.getBusinessId()).isNull();
        assertThat(column.name()).isEqualTo("business_id");
        assertThat(column.nullable()).isTrue();

        ticket.setBusinessId(42L);

        assertThat(ticket.getBusinessId()).isEqualTo(42L);
    }
}
