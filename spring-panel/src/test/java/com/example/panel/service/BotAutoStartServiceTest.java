package com.example.panel.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.entity.Channel;
import com.example.panel.model.channel.BotCredential;
import com.example.panel.repository.ChannelRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BotAutoStartServiceTest {

    @Mock
    private ChannelRepository channelRepository;

    @Mock
    private BotProcessService botProcessService;

    @Mock
    private SharedConfigService sharedConfigService;

    @InjectMocks
    private BotAutoStartService botAutoStartService;

    @Test
    void autoStartActiveBotsSkipsChannelWithoutId() {
        Channel channel = new Channel();
        channel.setId(null);
        channel.setChannelName("Unsaved");
        channel.setActive(true);

        when(channelRepository.findAll()).thenReturn(List.of(channel));

        botAutoStartService.autoStartActiveBots();

        verify(sharedConfigService, never()).loadBotCredentials();
        verify(botProcessService, never()).status(anyLong());
        verify(botProcessService, never()).start(any());
    }

    @Test
    void autoStartActiveBotsSkipsChannelWhenBoundCredentialIsDisabled() {
        Channel channel = new Channel();
        channel.setId(42L);
        channel.setChannelName("VK Support");
        channel.setActive(true);
        channel.setCredentialId(5L);

        when(channelRepository.findAll()).thenReturn(List.of(channel));
        when(sharedConfigService.loadBotCredentials()).thenReturn(List.of(
            new BotCredential(5L, "VK Main", "vk", "vk-token", false)
        ));

        botAutoStartService.autoStartActiveBots();

        verify(botProcessService, never()).status(any());
        verify(botProcessService, never()).start(any());
    }

    @Test
    void autoStartActiveBotsStartsChannelWhenBoundCredentialIsActive() {
        Channel channel = new Channel();
        channel.setId(77L);
        channel.setChannelName("TG Support");
        channel.setActive(true);
        channel.setCredentialId(9L);

        when(channelRepository.findAll()).thenReturn(List.of(channel));
        when(sharedConfigService.loadBotCredentials()).thenReturn(List.of(
            new BotCredential(9L, "TG Main", "telegram", "tg-token", true)
        ));
        when(botProcessService.status(77L))
            .thenReturn(new BotProcessService.BotProcessStatus(false, "stopped", null));
        when(botProcessService.start(channel))
            .thenReturn(new BotProcessService.BotProcessStatus(true, "running", null));

        botAutoStartService.autoStartActiveBots();

        verify(botProcessService).status(77L);
        verify(botProcessService).start(channel);
    }

    @Test
    void autoStartActiveBotsStartsChannelWithoutCredentialBinding() {
        Channel channel = new Channel();
        channel.setId(66L);
        channel.setChannelName("No Credential Binding");
        channel.setActive(true);
        channel.setCredentialId(null);

        when(channelRepository.findAll()).thenReturn(List.of(channel));
        when(botProcessService.status(66L))
            .thenReturn(new BotProcessService.BotProcessStatus(false, "stopped", null));
        when(botProcessService.start(channel))
            .thenReturn(new BotProcessService.BotProcessStatus(true, "running", null));

        botAutoStartService.autoStartActiveBots();

        verify(sharedConfigService, never()).loadBotCredentials();
        verify(botProcessService).status(66L);
        verify(botProcessService).start(channel);
    }

    @Test
    void autoStartActiveBotsSkipsInactiveChannel() {
        Channel channel = new Channel();
        channel.setId(55L);
        channel.setChannelName("Archived");
        channel.setActive(false);

        when(channelRepository.findAll()).thenReturn(List.of(channel));

        botAutoStartService.autoStartActiveBots();

        verify(sharedConfigService, never()).loadBotCredentials();
        verify(botProcessService, never()).status(anyLong());
        verify(botProcessService, never()).start(any());
    }

    @Test
    void autoStartActiveBotsSkipsAlreadyRunningChannel() {
        Channel channel = new Channel();
        channel.setId(88L);
        channel.setChannelName("Already Running");
        channel.setActive(true);
        channel.setCredentialId(12L);

        when(channelRepository.findAll()).thenReturn(List.of(channel));
        when(sharedConfigService.loadBotCredentials()).thenReturn(List.of(
            new BotCredential(12L, "TG Main", "telegram", "tg-token", true)
        ));
        when(botProcessService.status(88L))
            .thenReturn(new BotProcessService.BotProcessStatus(true, "running", OffsetDateTime.parse("2026-04-23T12:00:00Z")));

        botAutoStartService.autoStartActiveBots();

        verify(botProcessService).status(88L);
        verify(botProcessService, never()).start(any());
    }

    @Test
    void autoStartActiveBotsStartsChannelWhenCredentialIsMissingFromSharedConfig() {
        Channel channel = new Channel();
        channel.setId(91L);
        channel.setChannelName("Fallback Start");
        channel.setActive(true);
        channel.setCredentialId(77L);

        when(channelRepository.findAll()).thenReturn(List.of(channel));
        when(sharedConfigService.loadBotCredentials()).thenReturn(List.of());
        when(botProcessService.status(91L))
            .thenReturn(new BotProcessService.BotProcessStatus(false, "stopped", null));
        when(botProcessService.start(channel))
            .thenReturn(new BotProcessService.BotProcessStatus(true, "running", null));

        botAutoStartService.autoStartActiveBots();

        verify(botProcessService).status(91L);
        verify(botProcessService).start(channel);
    }

    @Test
    void autoStartActiveBotsContinuesAfterFailedStartAndStartsNextChannel() {
        Channel failed = new Channel();
        failed.setId(101L);
        failed.setChannelName("First Fails");
        failed.setActive(true);
        failed.setCredentialId(31L);

        Channel succeeds = new Channel();
        succeeds.setId(102L);
        succeeds.setChannelName("Second Starts");
        succeeds.setActive(true);
        succeeds.setCredentialId(32L);

        when(channelRepository.findAll()).thenReturn(List.of(failed, succeeds));
        when(sharedConfigService.loadBotCredentials()).thenReturn(List.of(
            new BotCredential(31L, "First", "telegram", "token-1", true),
            new BotCredential(32L, "Second", "telegram", "token-2", true)
        ));
        when(botProcessService.status(101L))
            .thenReturn(new BotProcessService.BotProcessStatus(false, "stopped", null));
        when(botProcessService.status(102L))
            .thenReturn(new BotProcessService.BotProcessStatus(false, "stopped", null));
        when(botProcessService.start(failed))
            .thenReturn(new BotProcessService.BotProcessStatus(false, "process exited early", null));
        when(botProcessService.start(succeeds))
            .thenReturn(new BotProcessService.BotProcessStatus(true, "running", null));

        botAutoStartService.autoStartActiveBots();

        verify(botProcessService).start(failed);
        verify(botProcessService).start(succeeds);
    }

    @Test
    void autoStartActiveBotsSkipsChannelWhenStatusReturnsNull() {
        Channel channel = new Channel();
        channel.setId(111L);
        channel.setChannelName("Null Status");
        channel.setActive(true);
        channel.setCredentialId(41L);

        when(channelRepository.findAll()).thenReturn(List.of(channel));
        when(sharedConfigService.loadBotCredentials()).thenReturn(List.of(
            new BotCredential(41L, "Null Status", "telegram", "token", true)
        ));
        when(botProcessService.status(111L)).thenReturn(null);
        when(botProcessService.start(channel))
            .thenReturn(new BotProcessService.BotProcessStatus(true, "running", null));

        botAutoStartService.autoStartActiveBots();

        verify(botProcessService).status(111L);
        verify(botProcessService).start(channel);
    }

    @Test
    void autoStartActiveBotsContinuesWhenStartReturnsNullAndProcessesNextChannel() {
        Channel first = new Channel();
        first.setId(112L);
        first.setChannelName("Null Start");
        first.setActive(true);
        first.setCredentialId(42L);

        Channel second = new Channel();
        second.setId(113L);
        second.setChannelName("Starts After Null");
        second.setActive(true);
        second.setCredentialId(43L);

        when(channelRepository.findAll()).thenReturn(List.of(first, second));
        when(sharedConfigService.loadBotCredentials()).thenReturn(List.of(
            new BotCredential(42L, "Null Start", "telegram", "token-1", true),
            new BotCredential(43L, "Second", "telegram", "token-2", true)
        ));
        when(botProcessService.status(112L))
            .thenReturn(new BotProcessService.BotProcessStatus(false, "stopped", null));
        when(botProcessService.status(113L))
            .thenReturn(new BotProcessService.BotProcessStatus(false, "stopped", null));
        when(botProcessService.start(first)).thenReturn(null);
        when(botProcessService.start(second))
            .thenReturn(new BotProcessService.BotProcessStatus(true, "running", null));

        botAutoStartService.autoStartActiveBots();

        verify(botProcessService).start(first);
        verify(botProcessService).start(second);
    }

    @Test
    void autoStartActiveBotsContinuesWhenStatusThrowsAndStartsNextChannel() {
        Channel failed = new Channel();
        failed.setId(114L);
        failed.setChannelName("Status Throws");
        failed.setActive(true);
        failed.setCredentialId(44L);

        Channel succeeds = new Channel();
        succeeds.setId(115L);
        succeeds.setChannelName("Starts After Status Error");
        succeeds.setActive(true);
        succeeds.setCredentialId(45L);

        when(channelRepository.findAll()).thenReturn(List.of(failed, succeeds));
        when(sharedConfigService.loadBotCredentials()).thenReturn(List.of(
            new BotCredential(44L, "Failed", "telegram", "token-1", true),
            new BotCredential(45L, "Succeeded", "telegram", "token-2", true)
        ));
        when(botProcessService.status(114L)).thenThrow(new IllegalStateException("status unavailable"));
        when(botProcessService.status(115L))
            .thenReturn(new BotProcessService.BotProcessStatus(false, "stopped", null));
        when(botProcessService.start(succeeds))
            .thenReturn(new BotProcessService.BotProcessStatus(true, "running", null));

        botAutoStartService.autoStartActiveBots();

        verify(botProcessService).status(114L);
        verify(botProcessService).status(115L);
        verify(botProcessService).start(succeeds);
    }

    @Test
    void autoStartActiveBotsContinuesWhenStartThrowsAndProcessesNextChannel() {
        Channel failed = new Channel();
        failed.setId(116L);
        failed.setChannelName("Start Throws");
        failed.setActive(true);
        failed.setCredentialId(46L);

        Channel succeeds = new Channel();
        succeeds.setId(117L);
        succeeds.setChannelName("Starts After Start Error");
        succeeds.setActive(true);
        succeeds.setCredentialId(47L);

        when(channelRepository.findAll()).thenReturn(List.of(failed, succeeds));
        when(sharedConfigService.loadBotCredentials()).thenReturn(List.of(
            new BotCredential(46L, "Failed", "telegram", "token-1", true),
            new BotCredential(47L, "Succeeded", "telegram", "token-2", true)
        ));
        when(botProcessService.status(116L))
            .thenReturn(new BotProcessService.BotProcessStatus(false, "stopped", null));
        when(botProcessService.status(117L))
            .thenReturn(new BotProcessService.BotProcessStatus(false, "stopped", null));
        when(botProcessService.start(failed)).thenThrow(new IllegalStateException("start failed"));
        when(botProcessService.start(succeeds))
            .thenReturn(new BotProcessService.BotProcessStatus(true, "running", null));

        botAutoStartService.autoStartActiveBots();

        verify(botProcessService).start(failed);
        verify(botProcessService).start(succeeds);
    }
}
