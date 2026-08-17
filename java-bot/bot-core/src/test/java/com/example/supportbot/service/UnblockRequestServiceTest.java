package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.supportbot.config.BotIntegrationTransportMode;
import com.example.supportbot.entity.ClientUnblockRequest;
import com.example.supportbot.repository.ClientUnblockRequestRepository;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class UnblockRequestServiceTest {

    @Test
    void countPendingUsesPanelClientInRabbitMode() {
        ClientUnblockRequestRepository repository = mock(ClientUnblockRequestRepository.class);
        PanelBlacklistClient panelBlacklistClient = mock(PanelBlacklistClient.class);
        when(panelBlacklistClient.isEnabled()).thenReturn(true);
        when(panelBlacklistClient.pendingSummary(0)).thenReturn(Optional.of(
            new PanelBlacklistClient.PendingUnblockSummary(4L, List.of())
        ));

        UnblockRequestService service = new UnblockRequestService(
            repository,
            new BotIntegrationTransportMode(new MockEnvironment().withProperty("app.integration.transport.mode", "rabbitmq")),
            panelBlacklistClient
        );

        assertThat(service.countPending()).isEqualTo(4L);
        verify(repository, never()).countByStatus("pending");
    }

    @Test
    void findRecentPendingUsesPanelClientInRabbitMode() {
        ClientUnblockRequestRepository repository = mock(ClientUnblockRequestRepository.class);
        PanelBlacklistClient panelBlacklistClient = mock(PanelBlacklistClient.class);
        when(panelBlacklistClient.isEnabled()).thenReturn(true);
        ClientUnblockRequest request = new ClientUnblockRequest();
        request.setId(901L);
        request.setUserId("77");
        request.setCreatedAt(OffsetDateTime.parse("2026-08-17T10:00:00Z"));
        when(panelBlacklistClient.pendingSummary(3)).thenReturn(Optional.of(
            new PanelBlacklistClient.PendingUnblockSummary(1L, List.of(request))
        ));

        UnblockRequestService service = new UnblockRequestService(
            repository,
            new BotIntegrationTransportMode(new MockEnvironment().withProperty("app.integration.transport.mode", "rabbitmq")),
            panelBlacklistClient
        );

        List<ClientUnblockRequest> recent = service.findRecentPending(3);

        assertThat(recent).containsExactly(request);
        verify(repository, never()).findByStatusOrderByCreatedAtDesc(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }
}
