package com.example.supportbot.service;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.supportbot.config.BotDatabaseRuntimeMode;
import com.example.supportbot.repository.ChatHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

class ChatHistoryServiceTest {

    @Test
    void constructorSkipsSchemaMutationOutsideSqliteMode() {
        ChatHistoryRepository historyRepository = mock(ChatHistoryRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UiEventOutboxService uiEventOutboxService = mock(UiEventOutboxService.class);
        ChatAttachmentMetadataService chatAttachmentMetadataService = mock(ChatAttachmentMetadataService.class);
        BotDatabaseRuntimeMode runtimeMode = new BotDatabaseRuntimeMode(
                new MockEnvironment().withProperty("spring.datasource.url", "jdbc:postgresql://localhost:5432/supportbot")
        );

        new ChatHistoryService(
                historyRepository,
                jdbcTemplate,
                uiEventOutboxService,
                chatAttachmentMetadataService,
                runtimeMode
        );

        verify(jdbcTemplate, never()).execute(any(String.class));
    }

    @Test
    void constructorKeepsLegacyColumnBootstrapForSqliteMode() {
        ChatHistoryRepository historyRepository = mock(ChatHistoryRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UiEventOutboxService uiEventOutboxService = mock(UiEventOutboxService.class);
        ChatAttachmentMetadataService chatAttachmentMetadataService = mock(ChatAttachmentMetadataService.class);
        BotDatabaseRuntimeMode runtimeMode = new BotDatabaseRuntimeMode(new MockEnvironment());

        new ChatHistoryService(
                historyRepository,
                jdbcTemplate,
                uiEventOutboxService,
                chatAttachmentMetadataService,
                runtimeMode
        );

        verify(jdbcTemplate, times(1)).execute(eq("ALTER TABLE chat_history ADD COLUMN original_message TEXT"));
        verify(jdbcTemplate, times(1)).execute(eq("ALTER TABLE chat_history ADD COLUMN forwarded_from TEXT"));
        verify(jdbcTemplate, times(1)).execute(eq("ALTER TABLE chat_history ADD COLUMN file_name TEXT"));
    }
}
