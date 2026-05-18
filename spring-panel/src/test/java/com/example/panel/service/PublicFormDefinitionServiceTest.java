package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.model.publicform.PublicFormConfig;
import com.example.panel.model.publicform.PublicFormQuestion;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicFormDefinitionServiceTest {

    @Test
    void buildConfigEnrichesLocationQuestionsAndNormalizesDisabledStatus() {
        SettingsCatalogService settingsCatalogService = mock(SettingsCatalogService.class);
        IikoDepartmentLocationCatalogService locationCatalogService = mock(IikoDepartmentLocationCatalogService.class);
        when(locationCatalogService.loadCatalog()).thenReturn(new IikoDepartmentLocationCatalogService.LocationCatalogSnapshot(
                Map.of("БлинБери", Map.of()),
                Map.of(),
                "shared_config",
                true,
                List.of()
        ));
        when(settingsCatalogService.buildLocationPresets(anyMap(), anyMap())).thenReturn(Map.of(
                "locations", Map.of(
                        "fields", Map.of(
                                "business", Map.of("options", List.of("БлинБери")),
                                "city", Map.of("tree", Map.of("БлинБери", Map.of("Корпоративная сеть", List.of("Москва"))))
                        )
                )
        ));

        PublicFormDefinitionService service = new PublicFormDefinitionService(
                new ObjectMapper(),
                new PublicFormRuntimeConfigService(mock(SharedConfigService.class)),
                settingsCatalogService,
                locationCatalogService
        );

        Channel channel = new Channel();
        channel.setId(7L);
        channel.setChannelName("Web Form");
        channel.setPublicId("web-config");
        channel.setQuestionsCfg("""
                {"schemaVersion":2,"enabled":true,"captchaEnabled":true,"disabledStatus":999,
                  "successInstruction":"  Ответим позже  ","responseEtaMinutes":120,
                  "fields":[
                    {"id":"city","text":"Город","type":"text","order":2},
                    {"id":"business","text":"Бизнес","type":"text","order":1}
                  ]}
                """);

        PublicFormConfig config = service.buildConfig(channel);

        assertThat(config.schemaVersion()).isEqualTo(2);
        assertThat(config.captchaEnabled()).isTrue();
        assertThat(config.disabledStatus()).isEqualTo(404);
        assertThat(config.successInstruction()).isEqualTo("Ответим позже");
        assertThat(config.questions()).extracting(PublicFormQuestion::id).containsExactly("business", "city");
        assertThat(config.questions()).extracting(PublicFormQuestion::type).containsOnly("select");
        assertThat(config.questions().get(0).metadata()).containsEntry("options", List.of("БлинБери"));
        assertThat(config.questions().get(0).metadata()).containsEntry("placeholder", "Выберите вариант");
        assertThat(config.questions().get(1).metadata()).containsKey("tree");
    }

    @Test
    void buildConfigSupportsLegacyArrayPayload() {
        SettingsCatalogService settingsCatalogService = mock(SettingsCatalogService.class);
        IikoDepartmentLocationCatalogService locationCatalogService = mock(IikoDepartmentLocationCatalogService.class);
        when(locationCatalogService.loadCatalog()).thenReturn(new IikoDepartmentLocationCatalogService.LocationCatalogSnapshot(
                Map.of(),
                Map.of(),
                "shared_config",
                true,
                List.of()
        ));
        when(settingsCatalogService.buildLocationPresets(anyMap(), anyMap())).thenReturn(Map.of());

        PublicFormDefinitionService service = new PublicFormDefinitionService(
                new ObjectMapper(),
                new PublicFormRuntimeConfigService(mock(SharedConfigService.class)),
                settingsCatalogService,
                locationCatalogService
        );

        Channel channel = new Channel();
        channel.setId(9L);
        channel.setChannelName("Legacy Form");
        channel.setQuestionsCfg("""
                [{"id":"topic","text":"Тема","type":"text","order":5,"required":true}]
                """);

        PublicFormConfig config = service.buildConfig(channel);

        assertThat(config.schemaVersion()).isEqualTo(1);
        assertThat(config.enabled()).isTrue();
        assertThat(config.captchaEnabled()).isFalse();
        assertThat(config.disabledStatus()).isEqualTo(404);
        assertThat(config.channelPublicId()).isEqualTo("9");
        assertThat(config.questions()).singleElement().satisfies(question -> {
            assertThat(question.id()).isEqualTo("topic");
            assertThat(question.text()).isEqualTo("Тема");
            assertThat(question.type()).isEqualTo("text");
            assertThat(question.metadata()).containsEntry("required", true);
        });
    }
}
