package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DialogNotificationServiceTest {

    private SharedConfigService sharedConfigService;
    private DialogNotificationService service;

    @BeforeEach
    void setUp() {
        sharedConfigService = mock(SharedConfigService.class);
        service = new DialogNotificationService(
                mock(JdbcTemplate.class),
                mock(ChannelRepository.class),
                mock(IntegrationNetworkService.class),
                sharedConfigService,
                new BotSettingsPayloadNormalizer(),
                new ObjectMapper()
        );
    }

    @Test
    void resolveRatingPromptUsesGlobalActiveTemplate() {
        when(sharedConfigService.loadSettings()).thenReturn(settingsWithRatingTemplates(
                List.of(
                        ratingTemplate("rating-default", "Пожалуйста, оцените качество ответа от 1 до 5."),
                        ratingTemplate("rating-alt", "Оцените заявку от 1 до 5.")
                ),
                "rating-default"
        ));

        assertEquals(
                "Пожалуйста, оцените качество ответа от 1 до 5.",
                service.resolveRatingPrompt(null)
        );
    }

    @Test
    void resolveRatingPromptUsesChannelOverrideWhenTemplateExists() {
        when(sharedConfigService.loadSettings()).thenReturn(settingsWithRatingTemplates(
                List.of(
                        ratingTemplate("rating-default", "Пожалуйста, оцените качество ответа от 1 до 5."),
                        ratingTemplate("rating-max", "Оцените поддержку MAX по шкале 1-5.")
                ),
                "rating-default"
        ));
        Channel channel = new Channel();
        channel.setRatingTemplateId("rating-max");

        assertEquals(
                "Оцените поддержку MAX по шкале 1-5.",
                service.resolveRatingPrompt(channel)
        );
    }

    @Test
    void resolveRatingPromptFallsBackToActiveTemplateWhenChannelOverrideIsMissing() {
        when(sharedConfigService.loadSettings()).thenReturn(settingsWithRatingTemplates(
                List.of(
                        ratingTemplate("rating-default", "Пожалуйста, оцените качество ответа от 1 до 5.")
                ),
                "rating-default"
        ));
        Channel channel = new Channel();
        channel.setRatingTemplateId("missing-template");

        assertEquals(
                "Пожалуйста, оцените качество ответа от 1 до 5.",
                service.resolveRatingPrompt(channel)
        );
    }

    private Map<String, Object> settingsWithRatingTemplates(List<Map<String, Object>> templates, String activeTemplateId) {
        Map<String, Object> botSettings = new LinkedHashMap<>();
        botSettings.put("rating_templates", templates);
        botSettings.put("active_rating_template_id", activeTemplateId);

        Map<String, Object> settings = new LinkedHashMap<>();
        settings.put("bot_settings", botSettings);
        return settings;
    }

    private Map<String, Object> ratingTemplate(String id, String promptText) {
        Map<String, Object> template = new LinkedHashMap<>();
        template.put("id", id);
        template.put("name", id);
        template.put("prompt_text", promptText);
        template.put("scale_size", 5);
        template.put("responses", List.of(
                Map.of("value", 1, "text", "Спасибо за вашу оценку 1!"),
                Map.of("value", 5, "text", "Спасибо за вашу оценку 5!")
        ));
        return template;
    }
}
