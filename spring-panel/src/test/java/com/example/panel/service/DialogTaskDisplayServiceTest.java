package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;

class DialogTaskDisplayServiceTest {

    @Test
    void normalizesHistoricalTaskTitleAndBodyWithoutExposingTicketId() {
        DialogLookupReadService lookup = mock(DialogLookupReadService.class);
        when(lookup.resolveRequestNumber("d73d2eac-792e-31a7-947b-aac607333189"))
                .thenReturn("20260928-008");
        DialogTaskDisplayService service = new DialogTaskDisplayService(lookup);

        assertThat(service.normalizeTitle(
                "Проверить автозакрытый диалог #d73d2eac-792e-31a7-947b-aac607333189: Проверить ответ"))
                .isEqualTo("Проверить автозакрытое обращение №20260928-008: Проверить ответ");
        assertThat(service.normalizeTitle(
                "Обращение #d73d2eac-792e-31a7-947b-aac607333189: Клиент"))
                .isEqualTo("Обращение №20260928-008: Клиент");
        assertThat(service.normalizeNotificationText(
                "Новая задача «Проверить автозакрытый диалог #d73d2eac-792e-31a7-947b-aac607333189: Проверить ответ»"))
                .isEqualTo("Новая задача «Проверить автозакрытое обращение №20260928-008: Проверить ответ»");

        String body = service.normalizeBodyHtml(
                "<p>Создано из диалога #d73d2eac-792e-31a7-947b-aac607333189.</p>"
                        + "<a href=\"/dialogs/d73d2eac-792e-31a7-947b-aac607333189\">"
                        + "Открыть диалог #d73d2eac-792e-31a7-947b-aac607333189</a>");
        assertThat(body)
                .contains("Создано из обращения №20260928-008")
                .contains("Открыть обращение №20260928-008")
                .contains("href=\"/dialogs/d73d2eac-792e-31a7-947b-aac607333189\"")
                .doesNotContain("Открыть диалог #d73d2eac");
    }

    @Test
    void missingRequestNumberRemovesVisibleTicketId() {
        DialogLookupReadService lookup = mock(DialogLookupReadService.class);
        DialogTaskDisplayService service = new DialogTaskDisplayService(lookup);

        assertThat(service.normalizeTitle("Обращение #T-404: Клиент"))
                .isEqualTo("Обращение: Клиент")
                .doesNotContain("T-404");
        assertThat(service.buildAutoCloseTitle("T-404", "Проверить"))
                .isEqualTo("Проверить автозакрытое обращение: Проверить")
                .doesNotContain("T-404");
    }
}
