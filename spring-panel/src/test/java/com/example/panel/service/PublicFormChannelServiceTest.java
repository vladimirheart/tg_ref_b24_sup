package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.model.publicform.PublicFormConfig;
import com.example.panel.model.publicform.PublicFormSessionDto;
import com.example.panel.repository.ChannelRepository;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicFormChannelServiceTest {

    @Test
    void loadConfigRawReturnsDemoConfigWithoutRepositoryLookup() {
        ChannelRepository channelRepository = mock(ChannelRepository.class);
        PublicFormDefinitionService publicFormDefinitionService = mock(PublicFormDefinitionService.class);
        PublicFormSessionService publicFormSessionService = mock(PublicFormSessionService.class);
        PublicFormConfig demoConfig = new PublicFormConfig(0L, "demo", "Demo", 1, true, false, 404, null, null, List.of());
        when(publicFormDefinitionService.buildDemoConfig()).thenReturn(demoConfig);

        PublicFormChannelService service = new PublicFormChannelService(
                channelRepository,
                publicFormDefinitionService,
                publicFormSessionService
        );

        assertThat(service.loadConfigRaw(" demo ")).containsSame(demoConfig);
        verify(publicFormDefinitionService).buildDemoConfig();
    }

    @Test
    void loadConfigRawResolvesChannelByNumericIdAndBuildsConfig() {
        ChannelRepository channelRepository = mock(ChannelRepository.class);
        PublicFormDefinitionService publicFormDefinitionService = mock(PublicFormDefinitionService.class);
        PublicFormSessionService publicFormSessionService = mock(PublicFormSessionService.class);
        Channel channel = new Channel();
        channel.setId(42L);
        channel.setChannelName("Support");
        PublicFormConfig config = new PublicFormConfig(42L, "support", "Support", 1, true, false, 404, null, null, List.of());

        when(channelRepository.findByPublicIdIgnoreCase("42")).thenReturn(Optional.empty());
        when(channelRepository.findById(42L)).thenReturn(Optional.of(channel));
        when(publicFormDefinitionService.buildConfig(channel)).thenReturn(config);

        PublicFormChannelService service = new PublicFormChannelService(
                channelRepository,
                publicFormDefinitionService,
                publicFormSessionService
        );

        assertThat(service.loadConfigRaw("42")).containsSame(config);
        assertThat(service.resolveChannelId("42")).contains(42L);
    }

    @Test
    void buildContinuationOptionsBuildsTelegramDeepLinkAndPlatformMetadata() {
        ChannelRepository channelRepository = mock(ChannelRepository.class);
        PublicFormDefinitionService publicFormDefinitionService = mock(PublicFormDefinitionService.class);
        PublicFormSessionService publicFormSessionService = mock(PublicFormSessionService.class);
        Channel channel = new Channel();
        channel.setId(7L);
        channel.setPublicId("web-main");
        channel.setChannelName("Main Support");
        channel.setPlatform("telegram");
        channel.setBotName("Support Bot");
        channel.setBotUsername("@support_test_bot");

        when(channelRepository.findByPublicIdIgnoreCase("web-main")).thenReturn(Optional.of(channel));

        PublicFormChannelService service = new PublicFormChannelService(
                channelRepository,
                publicFormDefinitionService,
                publicFormSessionService
        );

        Map<String, Object> payload = service.buildContinuationOptions("web-main", "token 1");

        assertThat(payload).containsEntry("enabled", true);
        assertThat(payload).containsEntry("platform", "telegram");
        assertThat(payload).containsEntry("platformLabel", "Telegram");
        assertThat(payload).containsEntry("command", "/continue token 1");
        assertThat(payload).containsEntry("token", "token 1");
        assertThat(payload).containsEntry("openUrl", "https://t.me/support_test_bot?start=web_token+1");
        assertThat(payload).containsEntry("hint", "Откройте бота по ссылке или отправьте команду продолжения.");
    }

    @Test
    void findSessionDelegatesToSessionServiceForResolvedChannel() {
        ChannelRepository channelRepository = mock(ChannelRepository.class);
        PublicFormDefinitionService publicFormDefinitionService = mock(PublicFormDefinitionService.class);
        PublicFormSessionService publicFormSessionService = mock(PublicFormSessionService.class);
        Channel channel = new Channel();
        channel.setId(11L);
        channel.setPublicId("web-session");
        PublicFormSessionDto session = new PublicFormSessionDto(
                "token-1",
                "web-1234",
                11L,
                "web-session",
                "Анна",
                null,
                "anna",
                OffsetDateTime.parse("2026-01-01T10:15:30+03:00")
        );

        when(channelRepository.findByPublicIdIgnoreCase("web-session")).thenReturn(Optional.of(channel));
        when(publicFormSessionService.findSession(channel, "token-1")).thenReturn(Optional.of(session));

        PublicFormChannelService service = new PublicFormChannelService(
                channelRepository,
                publicFormDefinitionService,
                publicFormSessionService
        );

        assertThat(service.findSession("web-session", "token-1")).containsSame(session);
    }

    @Test
    void buildContinuationOptionsReturnsDisabledPayloadWhenChannelMissing() {
        ChannelRepository channelRepository = mock(ChannelRepository.class);
        PublicFormDefinitionService publicFormDefinitionService = mock(PublicFormDefinitionService.class);
        PublicFormSessionService publicFormSessionService = mock(PublicFormSessionService.class);
        when(channelRepository.findByPublicIdIgnoreCase("missing")).thenReturn(Optional.empty());

        PublicFormChannelService service = new PublicFormChannelService(
                channelRepository,
                publicFormDefinitionService,
                publicFormSessionService
        );

        assertThat(service.buildContinuationOptions("missing", "token-x")).containsEntry("enabled", false);
        assertThat(service.findSession("missing", "token-x")).isEmpty();
        assertThat(service.resolveChannelId("missing")).isEmpty();
    }
}
