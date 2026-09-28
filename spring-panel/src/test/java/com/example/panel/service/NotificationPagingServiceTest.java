package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.entity.Notification;
import com.example.panel.model.notification.NotificationPage;
import com.example.panel.repository.NotificationRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;

class NotificationPagingServiceTest {

    @Test
    void cursorPageUsesLimitPlusOneAndContinuesFromCreatedAtAndId() {
        NotificationRepository repository = mock(NotificationRepository.class);
        NotificationService service = new NotificationService(
                repository,
                mock(JdbcTemplate.class),
                mock(JdbcTemplate.class),
                mock(UiEventStreamService.class)
        );

        Notification newest = notification(103L, "2026-09-28T14:03:00Z");
        Notification boundary = notification(102L, "2026-09-28T14:02:00Z");
        Notification probe = notification(101L, "2026-09-28T14:01:00Z");
        when(repository.findByUserIdentityOrderByCreatedAtDescIdDesc(eq("operator"), any(Pageable.class)))
                .thenReturn(List.of(newest, boundary, probe));

        NotificationPage first = service.findPageForUser("Operator", null, 2);

        assertThat(first.items()).extracting(item -> item.id()).containsExactly(103L, 102L);
        assertThat(first.hasMore()).isTrue();
        assertThat(first.nextCursor()).isNotBlank();
        ArgumentCaptor<Pageable> firstPageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByUserIdentityOrderByCreatedAtDescIdDesc(eq("operator"), firstPageable.capture());
        assertThat(firstPageable.getValue().getPageSize()).isEqualTo(3);

        OffsetDateTime boundaryCreatedAt = boundary.getCreatedAt();
        Long boundaryId = boundary.getId();
        when(repository.findPageBefore(
                eq("operator"),
                eq(boundaryCreatedAt),
                eq(boundaryId),
                any(Pageable.class)
        )).thenReturn(List.of(probe));

        NotificationPage second = service.findPageForUser("operator", first.nextCursor(), 2);

        assertThat(second.items()).extracting(item -> item.id()).containsExactly(101L);
        assertThat(second.hasMore()).isFalse();
        assertThat(second.nextCursor()).isNull();
        verify(repository).findPageBefore(
                eq("operator"),
                eq(boundaryCreatedAt),
                eq(boundaryId),
                any(Pageable.class)
        );
    }

    @Test
    void pageSizeIsCappedAtTwentyAndInvalidCursorIsRejected() {
        NotificationRepository repository = mock(NotificationRepository.class);
        NotificationService service = new NotificationService(
                repository,
                mock(JdbcTemplate.class),
                mock(JdbcTemplate.class),
                mock(UiEventStreamService.class)
        );
        when(repository.findByUserIdentityOrderByCreatedAtDescIdDesc(eq("operator"), any(Pageable.class)))
                .thenReturn(List.of());

        service.findPageForUser("operator", null, 100);

        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findByUserIdentityOrderByCreatedAtDescIdDesc(eq("operator"), pageable.capture());
        assertThat(pageable.getValue().getPageSize()).isEqualTo(21);
        assertThatThrownBy(() -> service.findPageForUser("operator", "not-a-valid-cursor", 20))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Invalid notification cursor");
    }

    private Notification notification(Long id, String createdAt) {
        Notification notification = mock(Notification.class);
        when(notification.getId()).thenReturn(id);
        when(notification.getCreatedAt()).thenReturn(OffsetDateTime.parse(createdAt));
        when(notification.getText()).thenReturn("notification-" + id);
        when(notification.getUrl()).thenReturn("/dialogs/T-" + id);
        when(notification.getIsRead()).thenReturn(Boolean.FALSE);
        return notification;
    }
}
