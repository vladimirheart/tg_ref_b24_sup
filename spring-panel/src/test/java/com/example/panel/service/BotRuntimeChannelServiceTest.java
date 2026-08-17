package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BotRuntimeChannelServiceTest {

    @Test
    void resolveConfiguredChannelCreatesNewChannelWhenTokenMissing() {
        ChannelRepository repository = mock(ChannelRepository.class);
        when(repository.findByToken("bot-token")).thenReturn(Optional.empty());
        when(repository.findByPublicId(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any(Channel.class))).thenAnswer(invocation -> {
            Channel channel = invocation.getArgument(0);
            channel.setId(77L);
            return channel;
        });

        BotRuntimeChannelService service = new BotRuntimeChannelService(repository);

        Channel resolved = service.resolveConfiguredChannel(null, "bot-token", "Telegram", "telegram");

        assertThat(resolved.getId()).isEqualTo(77L);
        assertThat(resolved.getToken()).isEqualTo("bot-token");
        assertThat(resolved.getPublicId()).isNotBlank();
        assertThat(resolved.getPlatform()).isEqualTo("telegram");
    }

    @Test
    void resolveConfiguredChannelEnsuresPublicIdForExistingConfiguredChannel() {
        ChannelRepository repository = mock(ChannelRepository.class);
        Channel existing = new Channel();
        existing.setId(88L);
        existing.setChannelName("MAX");
        existing.setPlatform("max");
        when(repository.findById(88L)).thenReturn(Optional.of(existing));
        when(repository.findByPublicId(org.mockito.ArgumentMatchers.anyString())).thenReturn(Optional.empty());
        when(repository.save(existing)).thenReturn(existing);

        BotRuntimeChannelService service = new BotRuntimeChannelService(repository);

        Channel resolved = service.resolveConfiguredChannel(88L, "max-token", "MAX", "max");

        assertThat(resolved.getPublicId()).isNotBlank();
        verify(repository, never()).findByToken(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void updateSupportChatIdPersistsNormalizedValue() {
        ChannelRepository repository = mock(ChannelRepository.class);
        Channel existing = new Channel();
        existing.setId(90L);
        existing.setSupportChatId("old");
        when(repository.findById(90L)).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);

        BotRuntimeChannelService service = new BotRuntimeChannelService(repository);

        Channel updated = service.updateSupportChatId(90L, " -10090 ");

        assertThat(updated.getSupportChatId()).isEqualTo("-10090");
        verify(repository).save(existing);
    }
}
