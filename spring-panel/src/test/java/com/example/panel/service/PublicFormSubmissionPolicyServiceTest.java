package com.example.panel.service;

import com.example.panel.model.publicform.PublicFormConfig;
import com.example.panel.model.publicform.PublicFormQuestion;
import com.example.panel.model.publicform.PublicFormSubmission;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicFormSubmissionPolicyServiceTest {

    @Test
    void prepareSubmissionStripsHtmlBuildsSummaryAndResolvesClientNameFromAnswers() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                "dialog_config", Map.of("public_form_strip_html_tags", true)
        ));
        PublicFormSubmissionPolicyService service = new PublicFormSubmissionPolicyService(
                new PublicFormRuntimeConfigService(sharedConfigService),
                new ObjectMapper()
        );
        PublicFormConfig config = new PublicFormConfig(
                1L,
                "web-demo",
                "Demo",
                1,
                true,
                false,
                404,
                null,
                null,
                List.of(new PublicFormQuestion("client_name", "Имя", "text", 1, Map.of("required", true)))
        );

        PublicFormSubmissionPolicyService.PreparedSubmission prepared = service.prepareSubmission(
                config,
                new PublicFormSubmission(
                        "<b>Нужна помощь</b>",
                        null,
                        "+79990000000",
                        "anna",
                        null,
                        Map.of("client_name", "<i>Анна</i>"),
                        null
                )
        );

        assertThat(prepared.submission().message()).isEqualTo("Нужна помощь");
        assertThat(prepared.answers()).containsEntry("client_name", "Анна");
        assertThat(prepared.clientName()).isEqualTo("Анна");
        assertThat(prepared.combinedMessage()).contains("Ответы формы:");
        assertThat(prepared.combinedMessage()).contains("Имя: Анна");
        assertThat(prepared.combinedMessage()).contains("Нужна помощь");
    }

    @Test
    void prepareSubmissionRejectsInvalidLocationCombination() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of());
        PublicFormSubmissionPolicyService service = new PublicFormSubmissionPolicyService(
                new PublicFormRuntimeConfigService(sharedConfigService),
                new ObjectMapper()
        );
        PublicFormConfig config = new PublicFormConfig(
                2L,
                "web-location",
                "Location",
                1,
                true,
                false,
                404,
                null,
                null,
                List.of(
                        new PublicFormQuestion("business", "Бизнес", "select", 1, Map.of("options", List.of("БлинБери"))),
                        new PublicFormQuestion("location_type", "Тип", "select", 2, Map.of(
                                "tree", Map.of("БлинБери", List.of("Корпоративная сеть"))
                        )),
                        new PublicFormQuestion("city", "Город", "select", 3, Map.of(
                                "tree", Map.of("БлинБери", Map.of("Корпоративная сеть", List.of("Москва")))
                        ))
                )
        );

        assertThatThrownBy(() -> service.prepareSubmission(
                config,
                new PublicFormSubmission(
                        "Нужна помощь",
                        "Анна",
                        "+79990000000",
                        "anna",
                        null,
                        Map.of(
                                "business", "БлинБери",
                                "location_type", "Корпоративная сеть",
                                "city", "Пенза"
                        ),
                        null
                )
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("недопустимое значение");
    }

    @Test
    void prepareSubmissionRejectsCaptchaMismatch() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                "dialog_config", Map.of(
                        "public_form_captcha_mode", "shared_secret",
                        "public_form_captcha_shared_secret", "good-secret"
                )
        ));
        PublicFormSubmissionPolicyService service = new PublicFormSubmissionPolicyService(
                new PublicFormRuntimeConfigService(sharedConfigService),
                new ObjectMapper()
        );
        PublicFormConfig config = new PublicFormConfig(
                3L,
                "web-captcha",
                "Captcha",
                1,
                true,
                true,
                404,
                null,
                null,
                List.of()
        );

        assertThatThrownBy(() -> service.prepareSubmission(
                config,
                new PublicFormSubmission("Нужна помощь", "Анна", null, null, "bad-secret", Map.of(), null)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CAPTCHA");
    }

    @Test
    void prepareSubmissionRespectsAnswersPayloadLimit() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                "dialog_config", Map.of("public_form_answers_total_max_length", 200)
        ));
        PublicFormSubmissionPolicyService service = new PublicFormSubmissionPolicyService(
                new PublicFormRuntimeConfigService(sharedConfigService),
                new ObjectMapper()
        );
        PublicFormConfig config = new PublicFormConfig(
                4L,
                "web-payload",
                "Payload",
                1,
                true,
                false,
                404,
                null,
                null,
                List.of(new PublicFormQuestion("details", "Детали", "text", 1, Map.of()))
        );
        String oversizedAnswer = "x".repeat(201);

        assertThatThrownBy(() -> service.prepareSubmission(
                config,
                new PublicFormSubmission("Нужна помощь", "Анна", null, null, null, Map.of("details", oversizedAnswer), null)
        )).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Суммарный объём");
    }
}
