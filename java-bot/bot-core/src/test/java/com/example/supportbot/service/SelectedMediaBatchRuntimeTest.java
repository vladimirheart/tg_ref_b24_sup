package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import com.example.supportbot.config.BotIntegrationTransportMode;
import com.example.supportbot.entity.Channel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;

class SelectedMediaBatchRuntimeTest {
    @ParameterizedTest @ValueSource(strings={"jdbc","disabled","inactive","telegram","max","null","zero"})
    void vkBatchAdmissionRequiresCanonicalRabbitRuntimeWithoutLocalFallback(String reason) {
        var local=mock(SharedConfigService.class);var panel=mock(PanelRuntimeConfigClient.class);
        when(panel.isEnabled()).thenReturn(!reason.equals("disabled"));
        var mode=new BotIntegrationTransportMode(new MockEnvironment().withProperty("app.integration.transport.mode",reason.equals("jdbc")?"jdbc":"rabbitmq"));
        var runtime=new RuntimeConfigService(local,mode,panel,new ObjectMapper());
        var channel=new Channel();channel.setId(reason.equals("zero")?0L:55L);channel.setActive(!reason.equals("inactive"));channel.setPlatform(reason.equals("telegram")?"telegram":reason.equals("max")?"max":"vk");
        var selected=new PanelRuntimeConfigClient.SelectedQuestionnaire(4,55,"GENERAL","USER_SELECTION",null,"general","a".repeat(64),"c".repeat(64),Map.of(),Map.of(),Map.of());
        assertThatThrownBy(()->runtime.selectedVkMediaAdmission(reason.equals("null")?null:channel,901,"T-VK",selected))
            .isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);
        verify(panel,never()).selectedVkMediaAdmission(anyLong(),anyLong(),anyString(),any());verifyNoInteractions(local);
    }
}
