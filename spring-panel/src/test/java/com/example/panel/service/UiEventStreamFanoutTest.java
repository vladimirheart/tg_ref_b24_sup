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

        UiEventStreamService service = new UiEventStreamService(publisher);
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

        UiEventStreamService service = new UiEventStreamService(publisher);
        service.publishNotificationsChanged(" Operator@Example.COM ", "notification_created");

        verify(publisher).publish(
            eq("operator@example.com"),
            eq("notifications_changed"),
            any()
        );
    }
}
