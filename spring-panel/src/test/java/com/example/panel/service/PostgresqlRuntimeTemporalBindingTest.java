package com.example.panel.service;

import com.example.panel.storage.AttachmentObjectStorageService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PostgresqlRuntimeTemporalBindingTest {

    @Test
    void uiEventOutboxBindsCreatedAtAsOffsetDateTime() {
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate();
        UiEventOutboxAppendService service = new UiEventOutboxAppendService(jdbc);

        service.append("client_message_edited", "T-1", 1L, "edited", null, null, null);

        SqlCall call = jdbc.lastCall();
        assertThat(call.args()).hasSize(9);
        assertThat(call.args()[8]).isInstanceOf(OffsetDateTime.class);
    }

    @Test
    void attachmentMetadataBindsCreatedAndUpdatedAtAsOffsetDateTime() {
        CapturingJdbcTemplate jdbc = new CapturingJdbcTemplate();
        AttachmentObjectStorageService storage = mock(AttachmentObjectStorageService.class);
        when(storage.providerLabel()).thenReturn("local");
        ChatAttachmentMetadataService service = new ChatAttachmentMetadataService(jdbc, storage);

        service.upsertForChatHistory(10L, "T-2", 1L, "dialogs/T-2/file.png", "file.png", "image/png", 42L, "photo");

        SqlCall call = jdbc.lastCall();
        assertThat(call.args()).hasSize(13);
        assertThat(call.args()[11]).isInstanceOf(OffsetDateTime.class);
        assertThat(call.args()[12]).isInstanceOf(OffsetDateTime.class);
    }

    private static final class CapturingJdbcTemplate extends JdbcTemplate {
        private final List<SqlCall> calls = new ArrayList<>();

        @Override
        public int update(String sql, Object... args) {
            calls.add(new SqlCall(sql, args));
            return 1;
        }

        SqlCall lastCall() {
            assertThat(calls).isNotEmpty();
            return calls.get(calls.size() - 1);
        }
    }

    private record SqlCall(String sql, Object[] args) {
    }
}
