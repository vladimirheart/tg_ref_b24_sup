package com.example.panel.service;

import java.util.Map;
import java.util.Optional;
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
    @Test void notificationPublicationWaitsForCommitAndRollbackPublishesNothing() {
        var publisher=mock(UiEventFanoutPublisher.class);
        var service=new UiEventStreamService(publisher,uniqueTicketGuard(),mock(UiEventTicketAccessService.class));
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.publishNotificationsChanged("operator","created");
            org.mockito.Mockito.verifyNoInteractions(publisher);
            org.springframework.transaction.support.TransactionSynchronizationUtils.triggerAfterCommit();
            verify(publisher).publish(eq("operator"),eq("notifications_changed"),any());
        } finally {org.springframework.transaction.support.TransactionSynchronizationManager.clear();}
        org.mockito.Mockito.clearInvocations(publisher);
        org.springframework.transaction.support.TransactionSynchronizationManager.initSynchronization();
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            service.publishNotificationsChanged("operator","rolled_back");
            org.springframework.transaction.support.TransactionSynchronizationUtils.triggerAfterCompletion(
                org.springframework.transaction.support.TransactionSynchronization.STATUS_ROLLED_BACK);
            org.mockito.Mockito.verifyNoInteractions(publisher);
        } finally {org.springframework.transaction.support.TransactionSynchronizationManager.clear();}
    }
    @Test void heartbeatReconcilesSnapshotsEvenWhenAQueueEventWasMissed() {
        var service=new UiEventStreamService(mock(UiEventFanoutPublisher.class),uniqueTicketGuard(),mock(UiEventTicketAccessService.class));
        var push=mock(NotificationPushService.class);
        org.springframework.test.util.ReflectionTestUtils.setField(service,"notificationPush",push);
        var principal=new com.example.panel.security.PanelUserPrincipal(7,"operator","test",true,java.util.List.of(),3);
        var auth=org.springframework.security.authentication.UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities());
        service.connect(auth);service.sendHeartbeat();
        verify(push,org.mockito.Mockito.times(2)).request(new UiEventRecipient(7,"operator",3));
    }

    @Test
    void dialogsChangedUsesDistributedFanoutEnvelope() {
        UiEventFanoutPublisher publisher = mock(UiEventFanoutPublisher.class);
        when(publisher.publish(any(), any(), any(), any())).thenReturn(true);

        LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard = uniqueTicketGuard();
        UiEventStreamService service = new UiEventStreamService(publisher, legacyTicketIdJdbcGuard,
            ticketAccess());
        service.publishDialogsChanged("ticket_updated", "T-42");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);

        verify(publisher).publish(
            isNull(),
            eq("dialogs_changed"),
            payloadCaptor.capture(),
            eq(scope("T-42"))
        );

        assertThat(payloadCaptor.getValue())
            .containsEntry("reason", "ticket_updated")
            .containsEntry("ticketId", "T-42")
            .containsKey("emittedAt");
    }

    @Test
    void targetedNotificationsPreserveWhitespaceWhileFoldingCaseBeforeFanout() {
        UiEventFanoutPublisher publisher = mock(UiEventFanoutPublisher.class);
        when(publisher.publish(any(), any(), any())).thenReturn(true);

        UiEventStreamService service = new UiEventStreamService(publisher, uniqueTicketGuard(),
            mock(UiEventTicketAccessService.class));
        service.publishNotificationsChanged(" Operator@Example.COM ", "notification_created");

        verify(publisher).publish(
            eq(" operator@example.com "),
            eq("notifications_changed"),
            any()
        );
    }

    @Test
    void dialogsChangedSkipsAmbiguousLegacyTicketId() {
        UiEventFanoutPublisher publisher = mock(UiEventFanoutPublisher.class);
        LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard = mock(LegacyTicketIdJdbcGuard.class);
        UiEventStreamService service = new UiEventStreamService(publisher, legacyTicketIdJdbcGuard,
            mock(UiEventTicketAccessService.class));

        service.publishDialogsChanged("ticket_updated", "T-DUPLICATE");

        verify(legacyTicketIdJdbcGuard).hasUniqueNonGeneralTicket("T-DUPLICATE");
        verify(publisher, org.mockito.Mockito.never()).publish(any(), any(), any());
        verify(publisher, org.mockito.Mockito.never()).publish(any(), any(), any(), any());
    }

    @Test
    void ticketScopedFanoutPayloadsPreserveExactValidatedTicketId() {
        UiEventFanoutPublisher publisher = mock(UiEventFanoutPublisher.class);
        when(publisher.publish(any(), any(), any(), any())).thenReturn(true);
        LegacyTicketIdJdbcGuard guard = mock(LegacyTicketIdJdbcGuard.class);
        when(guard.hasUniqueNonGeneralTicket(" T-EXACT ")).thenReturn(true);
        UiEventStreamService service = new UiEventStreamService(publisher, guard,
            ticketAccess());

        service.publishDialogsChanged("edited", " T-EXACT ");
        service.publishDialogHistoryChanged(" T-EXACT ", 9L, "edited");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(publisher).publish(isNull(), eq("dialogs_changed"), payload.capture(), eq(scope(" T-EXACT ")));
        assertThat(payload.getValue()).containsEntry("ticketId", " T-EXACT ");
        verify(publisher).publish(isNull(), eq("dialog_history_changed"), payload.capture(), eq(scope(" T-EXACT ")));
        assertThat(payload.getValue()).containsEntry("ticketId", " T-EXACT ").containsEntry("channelId", 9L);
    }

    private UiEventTicketAccessService ticketAccess() {
        UiEventTicketAccessService access = mock(UiEventTicketAccessService.class);
        when(access.captureScope(any())).thenAnswer(call -> Optional.of(scope(call.getArgument(0))));
        return access;
    }

    private UiEventTicketScope scope(String id) {
        return new UiEventTicketScope(1L, id, "a".repeat(32), "BUSINESS", 7L);
    }

    private LegacyTicketIdJdbcGuard uniqueTicketGuard() {
        LegacyTicketIdJdbcGuard legacyTicketIdJdbcGuard = mock(LegacyTicketIdJdbcGuard.class);
        when(legacyTicketIdJdbcGuard.hasUniqueNonGeneralTicket(any())).thenReturn(true);
        return legacyTicketIdJdbcGuard;
    }
}
