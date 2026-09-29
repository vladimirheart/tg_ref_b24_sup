package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.entity.Notification;
import com.example.panel.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
import java.time.OffsetDateTime;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

class NotificationServiceTest {

    @Test
    void dialogBellTextUsesCanonicalRequestNumberAndDropsUuidPreview() {
        NotificationRepository repository = mock(NotificationRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JdbcTemplate usersJdbcTemplate = mock(JdbcTemplate.class);
        UiEventStreamService streamService = mock(UiEventStreamService.class);
        DialogLookupReadService dialogLookupReadService = mock(DialogLookupReadService.class);
        when(dialogLookupReadService.resolveRequestNumber("T-101")).thenReturn("20260928-007");

        NotificationService service = new NotificationService(repository, jdbcTemplate, usersJdbcTemplate, streamService);
        ReflectionTestUtils.setField(service, "dialogLookupReadService", dialogLookupReadService);

        service.notifyUser(
                "Operator",
                "Новое сообщение в обращении T-101: секретный preview",
                "/dialogs/T-101"
        );

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(repository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getUserIdentity()).isEqualTo("operator");
        assertThat(saved.getText()).isEqualTo("Новое сообщение в обращении №20260928-007");
        assertThat(saved.getText()).doesNotContain("T-101", "preview");
        assertThat(saved.getUrl()).isEqualTo("/dialogs/T-101");
        verify(dialogLookupReadService).resolveRequestNumber(eq("T-101"));
    }

    @Test
    void legacyStoredDialogNotificationIsCanonicalizedOnReadUsingUrl() {
        NotificationRepository repository = mock(NotificationRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        JdbcTemplate usersJdbcTemplate = mock(JdbcTemplate.class);
        UiEventStreamService streamService = mock(UiEventStreamService.class);
        DialogLookupReadService dialogLookupReadService = mock(DialogLookupReadService.class);
        when(dialogLookupReadService.resolveRequestNumber("T-102")).thenReturn("20260928-008");
        when(dialogLookupReadService.resolveRequestNumber("T-103")).thenReturn("20260928-009");

        Notification stored = new Notification();
        stored.setId(12L);
        stored.setUserIdentity("operator");
        stored.setText("Новое обращение T-102");
        stored.setUrl("/dialogs/T-102");
        stored.setIsRead(Boolean.FALSE);
        stored.setCreatedAt(OffsetDateTime.parse("2026-09-28T12:00:00Z"));

        Notification taskStored = new Notification();
        taskStored.setId(13L);
        taskStored.setUserIdentity("operator");
        taskStored.setText("Новая задача «Проверить автозакрытый диалог #T-103: Проверить»");
        taskStored.setUrl("/tasks");
        taskStored.setIsRead(Boolean.FALSE);
        taskStored.setCreatedAt(OffsetDateTime.parse("2026-09-28T12:01:00Z"));
        when(repository.findByUserIdentityOrderByCreatedAtDesc("operator")).thenReturn(List.of(stored, taskStored));

        NotificationService service = new NotificationService(repository, jdbcTemplate, usersJdbcTemplate, streamService);
        ReflectionTestUtils.setField(service, "dialogLookupReadService", dialogLookupReadService);
        ReflectionTestUtils.setField(service, "dialogTaskDisplayService", new DialogTaskDisplayService(dialogLookupReadService));

        var items = service.findForUser("operator");
        assertThat(items).hasSize(2);
        assertThat(items.get(0).text()).isEqualTo("Новое обращение №20260928-008");
        assertThat(items.get(0).text()).doesNotContain("T-102");
        assertThat(items.get(0).url()).isEqualTo("/dialogs/T-102");
        assertThat(items.get(1).text()).isEqualTo("Новая задача «Проверить автозакрытое обращение №20260928-009: Проверить»");
        assertThat(items.get(1).text()).doesNotContain("T-103");
        assertThat(items.get(1).url()).isEqualTo("/tasks");
    }

    @Test
    void autoCloseFormatterNeverFallsBackToUuid() {
        NotificationRepository repository = mock(NotificationRepository.class);
        NotificationService service = new NotificationService(
                repository,
                mock(JdbcTemplate.class),
                mock(JdbcTemplate.class),
                mock(UiEventStreamService.class)
        );
        DialogLookupReadService dialogLookupReadService = mock(DialogLookupReadService.class);
        ReflectionTestUtils.setField(service, "dialogLookupReadService", dialogLookupReadService);

        assertThat(service.formatAutoCloseText("d73d2eac-792e-31a7-947b-aac607333189"))
                .isEqualTo("Обращение автоматически закрыто из-за отсутствия активности.")
                .doesNotContain("d73d2eac");
    }
}
