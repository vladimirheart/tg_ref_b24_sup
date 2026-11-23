package com.example.panel.service;

import com.example.panel.security.PanelSessionVersionService;
import com.example.panel.security.PanelUserPrincipal;
import com.example.panel.config.MultibusinessFeatureFlags;
import com.example.panel.runtime.UiEventFanoutProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SessionRevocationStreamTest {
    @Test void recipientSnapshotsRetainVersionForBothStreamTypes() {
        var principal=new PanelUserPrincipal(7," reader ","erased",true,AuthorityUtils.createAuthorityList("PAGE_DIALOGS"),42);
        var auth=UsernamePasswordAuthenticationToken.authenticated(principal,null,principal.getAuthorities());
        assertThat(UiEventRecipient.from(auth).sessionVersion()).isEqualTo(42);
        assertThat(GeneralTicketActor.from(auth).sessionVersion()).isEqualTo(42);
    }

    @Test void bothWebReplicasCloseOldStreamsOnHeartbeatAndKeepNewGeneration() throws Exception {
        var versions=mock(PanelSessionVersionService.class);
        when(versions.isCurrent(7,"reader",2)).thenReturn(true);
        when(versions.isCurrent(7,"reader",3)).thenReturn(true);
        var one=ui(versions);var two=ui(versions);
        var oldOne=mock(SseEmitter.class);var oldTwo=mock(SseEmitter.class);var fresh=mock(SseEmitter.class);
        recipients(one).put(new UiEventRecipient(7,"reader",2),new CopyOnWriteArraySet<>(java.util.List.of(oldOne)));
        recipients(two).put(new UiEventRecipient(7,"reader",2),new CopyOnWriteArraySet<>(java.util.List.of(oldTwo)));
        recipients(two).put(new UiEventRecipient(7,"reader",3),new CopyOnWriteArraySet<>(java.util.List.of(fresh)));
        one.sendHeartbeat();two.sendHeartbeat();clearInvocations(oldOne,oldTwo,fresh);
        when(versions.isCurrent(7,"reader",2)).thenReturn(false);
        one.sendHeartbeat();two.sendHeartbeat();
        for(var old:java.util.List.of(oldOne,oldTwo)) {
            verify(old).send(argThat((SseEmitter.SseEventBuilder event) -> event.build().stream().anyMatch(data -> String.valueOf(data.getData()).contains("session_revoked"))));
            verify(old).complete();
        }
        verify(fresh,never()).complete();
        assertThat(recipients(one)).isEmpty();assertThat(recipients(two)).hasSize(1);
    }

    @Test void revokedUiStreamCannotReceiveNotificationPayload() throws Exception {
        var versions=mock(PanelSessionVersionService.class);var service=ui(versions);var emitter=mock(SseEmitter.class);
        recipients(service).put(new UiEventRecipient(7,"reader",2),new CopyOnWriteArraySet<>(java.util.List.of(emitter)));
        service.publishNotificationsChanged("reader","private-payload");
        verify(emitter).send(argThat((SseEmitter.SseEventBuilder event) -> event.build().stream().noneMatch(data -> String.valueOf(data.getData()).contains("private-payload"))));
        verify(emitter).complete();
    }

    @Test void generalStreamClosesBeforeAnyCurrentRightsOrPayloadLookup() throws Exception {
        var versions=mock(PanelSessionVersionService.class);var jdbc=mock(JdbcTemplate.class);
        var service=new GeneralTicketRealtimeService(jdbc,new MultibusinessFeatureFlags(),new UiEventFanoutProperties(),
            mock(StringRedisTemplate.class),new ObjectMapper(),versions);
        var emitter=mock(SseEmitter.class);
        @SuppressWarnings("unchecked") var recipients=(ConcurrentHashMap<GeneralTicketActor,CopyOnWriteArraySet<SseEmitter>>)
            ReflectionTestUtils.getField(service,"emittersByUser");
        recipients.put(new GeneralTicketActor(7,"reader",2),new CopyOnWriteArraySet<>(java.util.List.of(emitter)));
        service.sendHeartbeat();
        verify(emitter).send(argThat((SseEmitter.SseEventBuilder event) -> event.build().stream().anyMatch(data -> String.valueOf(data.getData()).contains("session_revoked"))));
        verify(emitter).complete();verifyNoInteractions(jdbc);assertThat(recipients).isEmpty();
    }

    private UiEventStreamService ui(PanelSessionVersionService versions) {
        return new UiEventStreamService(mock(UiEventFanoutPublisher.class),mock(LegacyTicketIdJdbcGuard.class),mock(UiEventTicketAccessService.class),versions);
    }
    @SuppressWarnings("unchecked") private ConcurrentHashMap<UiEventRecipient,CopyOnWriteArraySet<SseEmitter>> recipients(UiEventStreamService service) {
        return (ConcurrentHashMap<UiEventRecipient,CopyOnWriteArraySet<SseEmitter>>)ReflectionTestUtils.getField(service,"emittersByUser");
    }
}
