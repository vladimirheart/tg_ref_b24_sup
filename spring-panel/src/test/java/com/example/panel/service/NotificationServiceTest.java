package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.entity.Notification;
import com.example.panel.repository.NotificationRepository;
import org.junit.jupiter.api.Test;
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
        when(dialogLookupReadService.resolveRequestNumber("T-101")).thenReturn("20260928-7");

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
        assertThat(saved.getText()).isEqualTo("Новое сообщение в обращении №20260928-7");
        assertThat(saved.getText()).doesNotContain("T-101", "preview");
        assertThat(saved.getUrl()).isEqualTo("/dialogs/T-101");
        verify(dialogLookupReadService).resolveRequestNumber(eq("T-101"));
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
