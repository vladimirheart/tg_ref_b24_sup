package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.supportbot.config.BotIntegrationTransportMode;
import com.example.supportbot.entity.Channel;
import com.example.supportbot.repository.ChannelRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

class ChannelServiceTest {

    @Test
    void ensurePublicIdForTokenUsesPanelClientInRabbitMode() {
        ChannelRepository repository = mock(ChannelRepository.class);
        PanelChannelClient panelChannelClient = mock(PanelChannelClient.class);
        when(panelChannelClient.isEnabled()).thenReturn(true);
        Channel resolved = new Channel();
        resolved.setId(11L);
        resolved.setPublicId("public-11");
        when(panelChannelClient.resolveConfiguredChannel(null, "bot-token", "Telegram", "telegram"))
            .thenReturn(Optional.of(resolved));

        ChannelService service = new ChannelService(
            repository,
            new BotIntegrationTransportMode(new MockEnvironment().withProperty("app.integration.transport.mode", "rabbitmq")),
            panelChannelClient
        );

        Channel channel = service.ensurePublicIdForToken("bot-token");

        assertThat(channel.getPublicId()).isEqualTo("public-11");
        verify(panelChannelClient).resolveConfiguredChannel(null, "bot-token", "Telegram", "telegram");
        verify(repository, never()).findByToken(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void ensurePublicIdForTokenFailsFastWithoutPanelClientInRabbitMode() {
        ChannelRepository repository = mock(ChannelRepository.class);
        PanelChannelClient panelChannelClient = mock(PanelChannelClient.class);

        ChannelService service = new ChannelService(
            repository,
            new BotIntegrationTransportMode(new MockEnvironment().withProperty("app.integration.transport.mode", "rabbitmq")),
            panelChannelClient
        );

        assertThatThrownBy(() -> service.ensurePublicIdForToken("bot-token"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("internal panel channel API");

        verify(repository, never()).findByToken(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void supportChatConfigurationRequiresPanelInsteadOfChildApiOrJpa() {
        var repository=mock(com.example.supportbot.repository.ChannelRepository.class);
        var api=mock(PanelChannelClient.class);
        var mode=mock(com.example.supportbot.config.BotIntegrationTransportMode.class);
        var service=new ChannelService(repository,mode,api);var channel=new Channel();channel.setId(12L);
        assertThatThrownBy(()->service.updateSupportChatId(channel,"-10012"))
            .isInstanceOf(UnsupportedOperationException.class);
        org.mockito.Mockito.verifyNoInteractions(repository,api,mode);
    }

    @Test
    void findByIdUsesPanelClientInRabbitMode() {
        ChannelRepository repository = mock(ChannelRepository.class);
        PanelChannelClient panelChannelClient = mock(PanelChannelClient.class);
        when(panelChannelClient.isEnabled()).thenReturn(true);
        Channel resolved = new Channel();
        resolved.setId(14L);
        when(panelChannelClient.findById(14L)).thenReturn(Optional.of(resolved));

        ChannelService service = new ChannelService(
            repository,
            new BotIntegrationTransportMode(new MockEnvironment().withProperty("app.integration.transport.mode", "rabbitmq")),
            panelChannelClient
        );

        Optional<Channel> result = service.findById(14L);

        assertThat(result).contains(resolved);
        verify(panelChannelClient).findById(14L);
        verify(repository, never()).findById(14L);
    }

    @Test
    void resolveConfiguredChannelKeepsRepositoryFallbackInJdbcMode() {
        ChannelRepository repository = mock(ChannelRepository.class);
        PanelChannelClient panelChannelClient = mock(PanelChannelClient.class);
        Channel existing = new Channel();
        existing.setId(13L);
        existing.setPublicId("public-13");
        when(repository.findById(13L)).thenReturn(Optional.of(existing));

        ChannelService service = new ChannelService(
            repository,
            new BotIntegrationTransportMode(new MockEnvironment().withProperty("app.integration.transport.mode", "jdbc")),
            panelChannelClient
        );

        Channel resolved = service.resolveConfiguredChannel(13L, "vk-token", "VK", "vk");

        assertThat(resolved.getPublicId()).isEqualTo("public-13");
        verify(repository).findById(13L);
        verify(panelChannelClient, never()).resolveConfiguredChannel(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
