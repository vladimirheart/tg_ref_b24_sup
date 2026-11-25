package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.example.panel.entity.Channel;
import com.example.panel.entity.Notification;
import com.example.panel.model.notification.NotificationDto;
import com.example.panel.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class NotificationPresentationTest {
    private static final String ID = "bb300bf7-2de6-32b2-beb9-e322d581173d";

    @ParameterizedTest
    @ValueSource(strings = {"AI-агент эскалировал обращение %s. Причина", "Первая реакция просрочена в обращении %s.",
            "Сообщение в обращении %s было отредактировано", "В обращении %s обновлены категории",
            "К обращению %s подключен оператор", "Из обращения %s исключен оператор", "Диалог %s закрыт"})
    void allDialogReferencesUseNumberOnWriteAndRead(String template) {
        var repository = mock(NotificationRepository.class);
        var lookup = mock(DialogLookupReadService.class);
        when(lookup.resolveRequestNumber(ID)).thenReturn("20261125-003");
        var service = new NotificationService(repository, mock(JdbcTemplate.class), mock(JdbcTemplate.class),
                mock(UiEventStreamService.class), mock(LegacyTicketIdJdbcGuard.class));
        ReflectionTestUtils.setField(service, "dialogLookupReadService", lookup);
        String url = service.buildDialogUrl(ID);
        service.notifyUser("operator", template.formatted(ID), url);
        var saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getText()).isEqualTo(template.formatted("№20261125-003"));
        assertThat(saved.getValue().getUrl()).isEqualTo(url);
        Notification old = new Notification();
        old.setText(template.formatted(ID)); old.setUrl(url);
        when(repository.findVisibleForUser("operator", true)).thenReturn(java.util.List.of(old));
        var dto = service.findForUser("operator").get(0);
        assertThat(dto.text()).isEqualTo(template.formatted("№20261125-003"));
        assertThat(dto.source()).isEqualTo("dialogs");
        assertThat(dto.sourceLabel()).isEqualTo("Диалоги");
    }

    @Test
    void missingOrInvalidNumberNeverFallsBackToInternalId() {
        var lookup = mock(DialogLookupReadService.class);
        var service = new NotificationService(mock(NotificationRepository.class), mock(JdbcTemplate.class),
                mock(JdbcTemplate.class), mock(UiEventStreamService.class), mock(LegacyTicketIdJdbcGuard.class));
        ReflectionTestUtils.setField(service, "dialogLookupReadService", lookup);
        assertThat(service.formatTicketReference(ID)).isEqualTo("без присвоенного номера");
        when(lookup.resolveRequestNumber(ID)).thenReturn(ID);
        assertThat(service.formatTicketReference(ID)).doesNotContain(ID).isEqualTo("без присвоенного номера");
    }

    @Test
    void russianReferenceWordsIgnoreCaseButLegacyTicketIdsRemainExact() {
        var repository = mock(NotificationRepository.class);
        var lookup = mock(DialogLookupReadService.class);
        when(lookup.resolveRequestNumber("T-Case")).thenReturn("20261125-004");
        var service = new NotificationService(repository, mock(JdbcTemplate.class), mock(JdbcTemplate.class),
                mock(UiEventStreamService.class), mock(LegacyTicketIdJdbcGuard.class));
        ReflectionTestUtils.setField(service, "dialogLookupReadService", lookup);
        service.notifyUser("operator", "ДИАЛОГ T-Case закрыт", service.buildDialogUrl("T-Case"));
        service.notifyUser("operator", "Диалог t-case закрыт", service.buildDialogUrl("T-Case"));
        var saved = ArgumentCaptor.forClass(Notification.class);
        verify(repository, times(2)).save(saved.capture());
        assertThat(saved.getAllValues()).extracting(Notification::getText)
                .containsExactly("ДИАЛОГ №20261125-004 закрыт", "Диалог t-case закрыт");
    }

    @ParameterizedTest
    @CsvSource({"/dialogs?ticketId=x,dialogs,Диалоги", "/incidents/1,incidents,Инциденты",
            "/tasks/1,tasks,Задачи", "/projects?x=1,projects,Проекты", "/analytics/smtp-notifications,monitoring,Мониторинг",
            "/settings,settings,Настройки", "/knowledge-base,knowledge,База знаний",
            "//evil.test/dialogs,system,Система", "/dialogsevil,system,Система", "https://example.test/tasks,system,Система"})
    void sourceIsPresentationMetadataFromExactLocalRoute(String url, String source, String label) {
        var dto = new NotificationDto(1L, "notice", url, false, null);
        assertThat(dto.source()).isEqualTo(source);
        assertThat(dto.sourceLabel()).isEqualTo(label);
    }

    @Test
    void supportChatSeparatesServicePlatformAndChannelFromBody() {
        Channel channel = new Channel(); channel.setPlatform("telegram"); channel.setChannelName("Поддержка\nТест");
        assertThat(DialogNotificationService.formatSupportChatText("Диалоги", channel, "Обращение №20261125-003"))
                .isEqualTo("Диалоги · Telegram\nКанал: Поддержка Тест\n──────────\nОбращение №20261125-003");
        assertThat(DialogNotificationService.formatSupportChatText("SSL-сертификаты", channel, "Срок истёк"))
                .startsWith("SSL-сертификаты · Telegram\n").endsWith("──────────\nСрок истёк");
    }
}
