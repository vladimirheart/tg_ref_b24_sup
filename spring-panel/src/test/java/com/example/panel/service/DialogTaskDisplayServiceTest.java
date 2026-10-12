package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
    void titleAndReferenceResolveExactLegacyTicketId() {
        DialogLookupReadService lookup = mock(DialogLookupReadService.class);
        when(lookup.resolveRequestNumber(" T-100 ")).thenReturn("20261011-002");
        when(lookup.resolveRequestNumber("T-100")).thenReturn("20261011-001");
        DialogTaskDisplayService service = new DialogTaskDisplayService(lookup);

        assertThat(service.buildAutoCloseTitle(" T-100 ", "Проверить"))
            .isEqualTo("Проверить автозакрытое обращение №20261011-002: Проверить");
        assertThat(service.dialogReference(" T-100 ")).isEqualTo("обращение №20261011-002");
        verify(lookup, org.mockito.Mockito.never()).resolveRequestNumber("T-100");
    }

    @Test
    void historicalTaskTitleAndNotificationUseExactPaddedLegacyTicketId() {
        DialogLookupReadService lookup = mock(DialogLookupReadService.class);
        when(lookup.resolveRequestNumber(" T-100 ")).thenReturn("20261012-010");
        when(lookup.resolveRequestNumber(" null ")).thenReturn("20261012-011");
        when(lookup.resolveRequestNumber("T-100")).thenReturn("20261012-009");
        DialogTaskDisplayService service = new DialogTaskDisplayService(lookup);

        assertThat(service.normalizeTitle("Обращение # T-100 : Клиент"))
                .isEqualTo("Обращение №20261012-010: Клиент");
        assertThat(service.normalizeTitle("Проверить автозакрытый диалог # null : Ответ"))
                .isEqualTo("Проверить автозакрытое обращение №20261012-011: Ответ");
        assertThat(service.normalizeTitle("Обращение # T-100 "))
                .isEqualTo("Обращение №20261012-010");
        assertThat(service.normalizeTitle("Проверить автозакрытый диалог # null "))
                .isEqualTo("Проверить автозакрытое обращение №20261012-011");
        assertThat(service.normalizeNotificationText("Новая задача «Обращение # T-100 : Клиент»"))
                .isEqualTo("Новая задача «Обращение №20261012-010: Клиент»");
        verify(lookup, org.mockito.Mockito.never()).resolveRequestNumber("T-100");
        verify(lookup, org.mockito.Mockito.never()).resolveRequestNumber("null");
    }

    @Test
    void historicalBodyUsesExactPaddedTicketIdAndKeepsLinkUntouched() {
        DialogLookupReadService lookup = mock(DialogLookupReadService.class);
        when(lookup.resolveRequestNumber(" T-100 ")).thenReturn("20261012-047");
        when(lookup.resolveRequestNumber("T-100")).thenReturn("20261012-001");
        DialogTaskDisplayService service = new DialogTaskDisplayService(lookup);

        String body = service.normalizeBodyHtml(
                "<p>Создано из диалога # T-100 .</p>"
                + "<p><a href=\"/dialogs/%20T-100%20\">Открыть диалог # T-100 </a></p>");

        assertThat(body).contains("Создано из обращения №20261012-047.")
                .contains("Открыть обращение №20261012-047</a>")
                .contains("href=\"/dialogs/%20T-100%20\"")
                .doesNotContain("20261012-001");
        verify(lookup, org.mockito.Mockito.never()).resolveRequestNumber("T-100");
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
