package com.example.supportbot.service;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.supportbot.repository.ChatHistoryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ChatHistoryServiceTest {

    @Test
    void constructorDoesNotMutateSchema() {
        ChatHistoryRepository historyRepository = mock(ChatHistoryRepository.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        UiEventOutboxService uiEventOutboxService = mock(UiEventOutboxService.class);
        ChatAttachmentMetadataService chatAttachmentMetadataService = mock(ChatAttachmentMetadataService.class);

        new ChatHistoryService(
                historyRepository,
                jdbcTemplate,
                uiEventOutboxService,
                chatAttachmentMetadataService
        );

        verify(jdbcTemplate, never()).execute(any(String.class));
    }
}
