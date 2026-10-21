package com.example.supportbot.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.example.supportbot.config.BotIntegrationTransportMode;
import com.example.supportbot.entity.Channel;
import com.example.supportbot.settings.BotSettingsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RuntimeQuestionnaireStartTest {
    @Test void remoteStartUsesWriterGatedRequestsAndNeverLoadsLocalSettings() {
        var local=mock(SharedConfigService.class);var mode=mock(BotIntegrationTransportMode.class);
        var client=mock(PanelRuntimeConfigClient.class);when(mode.isRabbitMqMode()).thenReturn(true);when(client.isEnabled()).thenReturn(true);
        var runtime=new RuntimeConfigService(local,mode,client,new ObjectMapper());
        var channel=new Channel();channel.setId(55L);
        var catalog=new PanelRuntimeConfigClient.ChoiceCatalog(4,55,"c".repeat(64),List.of());
        when(client.choicesV4ForStart(55)).thenReturn(catalog);
        runtime.questionnaireChoices(channel);runtime.selectQuestionnaire(channel,catalog,7);
        verify(client).choicesV4ForStart(55);verify(client).selectForStart(catalog,7);
        verify(client,never()).choices(anyLong());verify(client,never()).select(any(),anyLong());verifyNoInteractions(local);
    }
    @ParameterizedTest @ValueSource(strings={"mode","disabled","missing-channel","missing-id","inactive","catalog-channel"})
    void disabledOrMismatchedStartCannotFallBackToLocalData(String reason) {
        var local=mock(SharedConfigService.class);var mode=mock(BotIntegrationTransportMode.class);var client=mock(PanelRuntimeConfigClient.class);
        when(mode.isRabbitMqMode()).thenReturn(!reason.equals("mode"));when(client.isEnabled()).thenReturn(!reason.equals("disabled"));
        var runtime=new RuntimeConfigService(local,mode,client,new ObjectMapper());
        var channel=new Channel();channel.setId(reason.equals("missing-id")?null:55L);channel.setActive(!reason.equals("inactive"));
        Channel input=reason.equals("missing-channel")?null:channel;
        var catalog=new PanelRuntimeConfigClient.ChoiceCatalog(1,999,"c".repeat(64),List.of());
        assertThatThrownBy(()->runtime.selectQuestionnaire(input,catalog,7))
            .isInstanceOf(PanelRuntimeConfigClient.ScopedRuntimeConfigUnavailableException.class);
        verifyNoInteractions(local);verify(client,never()).selectForStart(any(),anyLong());
    }
    @Test void selectedSettingsHaveNoChannelOverrideGlobalTemplatesOrLocationPresets() {
        var local=mock(SharedConfigService.class);
        var service=new BotSettingsService(new ObjectMapper().configure(
            com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false),local,mock(RuntimeConfigService.class));
        var template=Map.of("id","q-a","name","A","question_flow",
            List.of(Map.of("id","only-free","type","text","text","Private A","order",5)));
        var selected=new PanelRuntimeConfigClient.SelectedQuestionnaire(1,55,"BUSINESS","USER_SELECTION",7,"q-a",
            "a".repeat(64),"c".repeat(64),Map.of("active_template_id","q-a","question_templates",List.of(template)),Map.of(),Map.of());
        var settings=service.loadSelectedQuestionnaire(selected);
        assertThat(settings.getActiveTemplateId()).isEqualTo("q-a");
        assertThat(service.questionFlow(settings)).extracting(item->item.getId()).containsExactly("only-free");
        assertThat(service.questionFlow(settings)).extracting(item->item.getText()).containsExactly("Private A");
        verifyNoInteractions(local);
    }
    @Test void vkV4RequestsItsOwnMediaCapabilityForBothScopes() {
        var local=mock(SharedConfigService.class);var mode=mock(BotIntegrationTransportMode.class);var client=mock(PanelRuntimeConfigClient.class);
        when(mode.isRabbitMqMode()).thenReturn(true);when(client.isEnabled()).thenReturn(true);
        var runtime=new RuntimeConfigService(local,mode,client,new ObjectMapper());var channel=new Channel();channel.setId(55L);channel.setPlatform("vk");
        var catalog=new PanelRuntimeConfigClient.ChoiceCatalog(4,55,"c".repeat(64),List.of());
        when(client.choicesV4ForStart(55,true)).thenReturn(catalog);
        assertThat(runtime.questionnaireChoices(channel)).isSameAs(catalog);runtime.selectQuestionnaire(channel,catalog,7);runtime.selectGeneralQuestionnaire(channel,catalog);
        verify(client).selectForStart(catalog,7,true);verify(client).selectGeneralForStart(catalog,true);verifyNoInteractions(local);
    }
}
