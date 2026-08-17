package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.panel.entity.Channel;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class PanelBotSettingsServiceTest {

    @Test
    void resolveRatingPromptTemplateUsesChannelRatingTemplateOverride() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
            "bot_settings", Map.of(
                "rating_templates", List.of(
                    Map.of("id", "default", "scale_size", 5, "prompt_text", "Default {ticket_id}"),
                    Map.of("id", "channel-template", "scale_size", 3, "prompt_text", "Rate {ticket_id} from 1 to {scale}")
                ),
                "active_rating_template_id", "default"
            )
        ));
        PanelBotSettingsService service = new PanelBotSettingsService(sharedConfigService, new BotSettingsPayloadNormalizer());
        Channel channel = new Channel();
        channel.setRatingTemplateId("channel-template");

        PanelBotSettingsService.RatingPromptTemplate result = service.resolveRatingPromptTemplate(channel);

        assertThat(result.prompt()).isEqualTo("Rate {ticket_id} from 1 to {scale}");
        assertThat(result.scale()).isEqualTo(3);
    }

    @Test
    void resolveRatingPromptTemplateFallsBackToDefaultPromptAndScale() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());
        PanelBotSettingsService service = new PanelBotSettingsService(sharedConfigService, new BotSettingsPayloadNormalizer());

        PanelBotSettingsService.RatingPromptTemplate result = service.resolveRatingPromptTemplate(null);

        assertThat(result.prompt()).isEqualTo("Оцените заявку {ticket_id} по шкале 1-{scale}");
        assertThat(result.scale()).isEqualTo(5);
    }
}
