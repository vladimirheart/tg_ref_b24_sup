package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.supportbot.config.BotIntegrationTransportMode;
import com.example.supportbot.entity.ClientBlacklist;
import com.example.supportbot.entity.ClientUnblockRequest;
import com.example.supportbot.repository.ClientBlacklistRepository;
import com.example.supportbot.repository.ClientUnblockRequestRepository;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class BlacklistServiceTest {

    @Test
    void resolveStatusUsesPanelClientInRabbitMode() {
        ClientBlacklistRepository blacklistRepository = mock(ClientBlacklistRepository.class);
        ClientUnblockRequestRepository unblockRequestRepository = mock(ClientUnblockRequestRepository.class);
        PanelBlacklistClient panelBlacklistClient = mock(PanelBlacklistClient.class);
        when(panelBlacklistClient.isEnabled()).thenReturn(true);
        when(panelBlacklistClient.resolveStatus(77L, java.util.List.of("vk_77")))
            .thenReturn(Optional.of(new PanelBlacklistClient.ResolvedBlacklistStatus("vk_77", true, false)));

        BlacklistService service = new BlacklistService(
            blacklistRepository,
            unblockRequestRepository,
            new BotIntegrationTransportMode(new MockEnvironment().withProperty("app.integration.transport.mode", "rabbitmq")),
            panelBlacklistClient
        );

        BlacklistService.ResolvedBlacklistStatus result = service.resolveStatus(77L, "vk_77");

        assertThat(result.matchedUserId()).isEqualTo("vk_77");
        assertThat(result.status().blacklisted()).isTrue();
        assertThat(result.status().unblockRequested()).isFalse();
        verify(panelBlacklistClient).resolveStatus(77L, java.util.List.of("vk_77"));
        verify(blacklistRepository, never()).findById("77");
    }

    @Test
    void requestUnblockUsesPanelClientInRabbitMode() {
        ClientBlacklistRepository blacklistRepository = mock(ClientBlacklistRepository.class);
        ClientUnblockRequestRepository unblockRequestRepository = mock(ClientUnblockRequestRepository.class);
        PanelBlacklistClient panelBlacklistClient = mock(PanelBlacklistClient.class);
        when(panelBlacklistClient.isEnabled()).thenReturn(true);
        ClientUnblockRequest request = new ClientUnblockRequest();
        request.setId(1001L);
        request.setUserId("77");
        when(panelBlacklistClient.requestUnblock(77L, "", 12L, Duration.ofMinutes(10)))
            .thenReturn(Optional.of(new PanelBlacklistClient.UnblockRequestDecision(
                request,
                true,
                Duration.ZERO
            )));

        BlacklistService service = new BlacklistService(
            blacklistRepository,
            unblockRequestRepository,
            new BotIntegrationTransportMode(new MockEnvironment().withProperty("app.integration.transport.mode", "rabbitmq")),
            panelBlacklistClient
        );

        BlacklistService.UnblockRequestDecision decision = service.requestUnblock(77L, "", 12L, Duration.ofMinutes(10));

        assertThat(decision.created()).isTrue();
        assertThat(decision.request()).isSameAs(request);
        verify(panelBlacklistClient).requestUnblock(77L, "", 12L, Duration.ofMinutes(10));
        verify(unblockRequestRepository, never()).save(org.mockito.ArgumentMatchers.any(ClientUnblockRequest.class));
    }

    @Test
    void requestUnblockFailsFastWithoutPanelClientInRabbitMode() {
        ClientBlacklistRepository blacklistRepository = mock(ClientBlacklistRepository.class);
        ClientUnblockRequestRepository unblockRequestRepository = mock(ClientUnblockRequestRepository.class);
        PanelBlacklistClient panelBlacklistClient = mock(PanelBlacklistClient.class);

        BlacklistService service = new BlacklistService(
            blacklistRepository,
            unblockRequestRepository,
            new BotIntegrationTransportMode(new MockEnvironment().withProperty("app.integration.transport.mode", "rabbitmq")),
            panelBlacklistClient
        );

        assertThatThrownBy(() -> service.requestUnblock(77L, "", 12L, Duration.ofMinutes(10)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("internal panel blacklist API");

        verify(unblockRequestRepository, never()).save(org.mockito.ArgumentMatchers.any(ClientUnblockRequest.class));
    }
}
