package com.example.panel.service;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UiEventStreamFanoutTest {

    @Test
    void dialogsChangedUsesDistributedFanoutEnvelope() {
        UiEventFanoutPublisher publisher = mock(UiEventFanoutPublisher.class);
        when(publisher.publish(any(), any(), any())).thenReturn(true);

        LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard = uniqueTicketGuard();
        UiEventStreamService service = new UiEventStreamService(publisher, legacyTicketIdJdbcGuard);
        service.publishDialogsChanged("ticket_updated", "T-42");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);

        verify(publisher).publish(
            isNull(),
            eq("dialogs_changed"),
            payloadCaptor.capture()
        );

        assertThat(payloadCaptor.getValue())
            .containsEntry("reason", "ticket_updated")
            .containsEntry("ticketId", "T-42")
            .containsKey("emittedAt");
    }

    @Test
    void targetedNotificationsNormalizeIdentityBeforeFanout() {
        UiEventFanoutPublisher publisher = mock(UiEventFanoutPublisher.class);
        when(publisher.publish(any(), any(), any())).thenReturn(true);

        UiEventStreamService service = new UiEventStreamService(publisher, uniqueTicketGuard());
        service.publishNotificationsChanged(" Operator@Example.COM ", "notification_created");

        verify(publisher).publish(
            eq("operator@example.com"),
            eq("notifications_changed"),
            any()
        );
    }

    @Test
    void dialogsChangedSkipsAmbiguousLegacyTicketId() {
        UiEventFanoutPublisher publisher = mock(UiEventFanoutPublisher.class);
        LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard = mock(LegacyTicketIdJdbcGuard.class);
        UiEventStreamService service = new UiEventStreamService(publisher, legacyTicketIdJdbcGuard);

        service.publishDialogsChanged("ticket_updated", "T-DUPLICATE");

        verify(legacyTicketIdJdbcGuard).hasUniqueTicket("T-DUPLICATE");
        verify(publisher, org.mockito.Mockito.never()).publish(any(), any(), any());
    }

    private LegacyTicketIdJdbcGuard uniqueTicketGuard() {
        LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard = mock(LegacyTicketIdJdbcGuard.class);
        when(legacyTicketIdJdbcGuard.hasUniqueTicket(any())).thenReturn(true);
        return legacyTicketIdJdbcGuard;
    }
}
