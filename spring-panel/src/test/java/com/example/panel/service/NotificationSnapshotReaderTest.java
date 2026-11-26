package com.example.panel.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import org.junit.jupiter.api.Test;
import com.example.panel.security.PanelSessionVersionService;
import com.example.panel.model.notification.*;
import org.springframework.security.core.context.SecurityContextHolder;
import java.util.List;

class NotificationSnapshotReaderTest {
    @Test void readsCurrentAccountAndRestoresSecurityContext() {
        var notifications=mock(NotificationService.class);var sessions=mock(PanelSessionVersionService.class);
        var accounts=mock(SharedSystemAccessService.class);var recipient=new UiEventRecipient(7,"operator",3);
        when(sessions.isCurrent(7,"operator",3)).thenReturn(true);
        when(accounts.canAccess(any())).thenReturn(true);
        when(notifications.summary("operator")).thenAnswer(invocation->{
            var actor=GeneralTicketActor.from(SharedWorkAccessService.current());
            assertThat(actor.userId()).isEqualTo(7);assertThat(actor.sessionVersion()).isEqualTo(3);
            return new NotificationSummary(12);
        });
        when(notifications.findPageForUser("operator",null,10)).thenReturn(new NotificationPage(List.of(),null,false));
        var before=SecurityContextHolder.getContext();
        var result=new NotificationSnapshotReader(notifications,sessions,accounts).read(recipient);
        assertThat(result.unread()).isEqualTo(12);
        assertThat(SecurityContextHolder.getContext()).isSameAs(before);
        verify(notifications).findPageForUser("operator",null,10);
    }
    @Test void revokedVersionAndDisabledAccountCannotProduceSnapshot() {
        var notifications=mock(NotificationService.class);var sessions=mock(PanelSessionVersionService.class);
        var accounts=mock(SharedSystemAccessService.class);var recipient=new UiEventRecipient(7,"operator",3);
        var reader=new NotificationSnapshotReader(notifications,sessions,accounts);
        assertThatThrownBy(()->reader.read(recipient)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        when(sessions.isCurrent(7,"operator",3)).thenReturn(true);
        assertThatThrownBy(()->reader.read(recipient)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(notifications);
    }
}
