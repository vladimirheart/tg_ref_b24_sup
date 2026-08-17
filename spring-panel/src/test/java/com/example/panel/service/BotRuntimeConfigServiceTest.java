package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.panel.entity.Channel;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class BotRuntimeConfigServiceTest {

    @Test
    void findRuntimeConfigBuildsChannelScopedSnapshot() {
        BotRuntimeChannelService channelService = mock(BotRuntimeChannelService.class);
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        BotSettingsPayloadNormalizer normalizer = new BotSettingsPayloadNormalizer();
        ObjectMapper objectMapper = new ObjectMapper();

        Channel channel = new Channel();
        channel.setId(44L);
        channel.setQuestionTemplateId("q-channel");
        channel.setRatingTemplateId("rate-channel");
        when(channelService.findChannel(44L)).thenReturn(Optional.of(channel));
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
            "bot_settings", Map.of(
                "question_templates", java.util.List.of(
                    Map.of("id", "q-default", "question_flow", java.util.List.of()),
                    Map.of("id", "q-channel", "question_flow", java.util.List.of())
                ),
                "active_template_id", "q-default",
                "rating_templates", java.util.List.of(
                    Map.of("id", "rate-default", "scale_size", 5),
                    Map.of("id", "rate-channel", "scale_size", 3)
                ),
                "active_rating_template_id", "rate-default"
            )
        ));
        when(sharedConfigService.loadLocations()).thenReturn(objectMapper.valueToTree(Map.of(
            "tree", Map.of("Retail", Map.of("store", Map.of("Moscow", java.util.List.of("Tverskaya"))))
        )));
        when(sharedConfigService.presetDefinitions()).thenReturn(Map.of(
            "locations", Map.of("label", "Структура локаций")
        ));

        BotRuntimeConfigService service = new BotRuntimeConfigService(
            channelService,
            sharedConfigService,
            normalizer,
            objectMapper
        );

        BotRuntimeConfigService.RuntimeConfigLookup lookup = service.findRuntimeConfig(44L).orElseThrow();

        assertThat(lookup.channelId()).isEqualTo(44L);
        assertThat(lookup.botSettings()).containsEntry("active_template_id", "q-channel");
        assertThat(lookup.botSettings()).containsEntry("active_rating_template_id", "rate-channel");
        assertThat(lookup.locationTree()).containsKey("Retail");
        assertThat(lookup.presetDefinitions()).containsKey("locations");
    }
}
