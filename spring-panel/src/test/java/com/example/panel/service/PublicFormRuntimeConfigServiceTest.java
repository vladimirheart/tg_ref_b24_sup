package com.example.panel.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicFormRuntimeConfigServiceTest {

    @Test
    void resolveUiLocaleFallsBackForInvalidValue() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                "dialog_config", Map.of("public_form_default_locale", "de")
        ));

        PublicFormRuntimeConfigService service = new PublicFormRuntimeConfigService(sharedConfigService);

        assertThat(service.resolveUiLocale()).isEqualTo("auto");
    }

    @Test
    void resolveAnswersPayloadMaxLengthClampsConfiguredValue() {
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                "dialog_config", Map.of("public_form_answers_total_max_length", 100000)
        ));

        PublicFormRuntimeConfigService service = new PublicFormRuntimeConfigService(sharedConfigService);

        assertThat(service.resolveAnswersPayloadMaxLength()).isEqualTo(50000);
    }

    @Test
    void normalizeDisabledStatusAllowsOnlyGoneOrNotFound() {
        PublicFormRuntimeConfigService service = new PublicFormRuntimeConfigService(mock(SharedConfigService.class));

        assertThat(service.normalizeDisabledStatus(410)).isEqualTo(410);
        assertThat(service.normalizeDisabledStatus(999)).isEqualTo(404);
    }
}
