package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.supportbot.config.BotIntegrationTransportMode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class ChildRuntimeIsolationTest {
    @TempDir Path directory;
    @Test void rabbitChildrenNeverReadGlobalSharedFiles() throws Exception {
        Files.writeString(directory.resolve("settings.json"),"{\"private\":\"another-business\"}");
        Files.writeString(directory.resolve("locations.json"),"{\"private\":true}");
        Files.writeString(directory.resolve("org_structure.json"),"{\"private\":true}");
        var shared=new SharedConfigService(new ObjectMapper(),directory.toString());
        assertThat(shared.loadSettings()).containsKey("private");
        shared.configureRuntime(new MockEnvironment().withProperty("app.integration.transport.mode","rabbitmq"));
        assertThat(shared.loadSettings()).isEmpty();assertThat(shared.loadLocations()).isNull();assertThat(shared.loadOrgStructure()).isNull();
        assertThat(shared.presetDefinitions()).doesNotContainKey("private");
    }
    @ParameterizedTest @ValueSource(strings={"settings","locations","presets"})
    void missingPanelSnapshotNeverFallsBackToLocalBusinessData(String operation) {
        var shared=mock(SharedConfigService.class);var mode=mock(BotIntegrationTransportMode.class);
        when(mode.isRabbitMqMode()).thenReturn(true);var api=mock(PanelRuntimeConfigClient.class);when(api.isEnabled()).thenReturn(true);
        var channel=new com.example.supportbot.entity.Channel();channel.setId(22L);
        var runtime=new RuntimeConfigService(shared,mode,api,new ObjectMapper());
        assertThatThrownBy(()-> {switch(operation){case "settings"->runtime.findChannelConfig(channel);case "locations"->runtime.locationTree(channel);default->runtime.basePresetDefinitions(channel);}})
            .isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);
        verifyNoInteractions(shared);
    }
    @ParameterizedTest @ValueSource(strings={"telegram","vk","max"})
    void serviceChatAdmissionDenialNeverCallsProvider(String platform) {
        var provider=mock(OutboundMessenger.class);var identity=new OutboundSenderIdentity(22L,platform,"a".repeat(64));
        when(provider.platform()).thenReturn(platform);when(provider.senderIdentity()).thenReturn(identity);
        var admission=mock(PanelOutboundSenderAuthorizationClient.class);when(admission.authorize(identity)).thenReturn(true);
        when(admission.authorizeSupportChat(identity,"-100")).thenReturn(false);
        var channel=new com.example.supportbot.entity.Channel();channel.setId(22L);channel.setPlatform(platform);channel.setSupportChatId("-100");
        assertThat(new MessagingService(List.of(provider),admission).sendToSupportChat(channel,"private payload")).isFalse();
        verify(provider,never()).sendToSupportChat(anyString(),anyString());
    }
    @Test void chatRebindWhileAwaitingAdmissionNeverSends() {
        var provider=mock(OutboundMessenger.class);var identity=new OutboundSenderIdentity(22L,"max","a".repeat(64));
        when(provider.platform()).thenReturn("max");when(provider.senderIdentity()).thenReturn(identity);
        var channel=new com.example.supportbot.entity.Channel();channel.setId(22L);channel.setPlatform("max");channel.setSupportChatId("-100");
        var admission=mock(PanelOutboundSenderAuthorizationClient.class);when(admission.authorize(identity)).thenReturn(true);
        when(admission.authorizeSupportChat(identity,"-100")).thenAnswer(call->{channel.setSupportChatId("foreign");return true;});
        assertThat(new MessagingService(List.of(provider),admission).sendToSupportChat(channel,"private payload")).isFalse();
        verify(provider,never()).sendToSupportChat(anyString(),anyString());
    }
}
