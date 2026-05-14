package com.example.panel.service;

import com.example.panel.entity.Channel;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PublicFormSessionServiceTest {

    @Test
    void findSessionReturnsEmptyWhenTokenBlank() {
        PublicFormSessionService service = new PublicFormSessionService(
                mock(JdbcTemplate.class),
                new PublicFormRuntimeConfigService(mock(SharedConfigService.class))
        );

        assertThat(service.findSession(new Channel(), "   ")).isEmpty();
    }

    @Test
    void findSessionReturnsEmptyWhenNoSessionRowFound() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        SharedConfigService sharedConfigService = mock(SharedConfigService.class);
        when(sharedConfigService.loadSettings()).thenReturn(Map.of(
                "dialog_config", Map.of("public_form_session_token_rotate_on_read", false)
        ));
        @SuppressWarnings("unchecked")
        List<Object> emptyRows = List.of();
        when(jdbcTemplate.query(anyString(), any(org.springframework.jdbc.core.RowMapper.class), eq("token-1"), eq(5L)))
                .thenReturn((List) emptyRows);

        PublicFormSessionService service = new PublicFormSessionService(
                jdbcTemplate,
                new PublicFormRuntimeConfigService(sharedConfigService)
        );
        Channel channel = new Channel();
        channel.setId(5L);
        channel.setPublicId("web-5");

        assertThat(service.findSession(channel, "token-1")).isEmpty();
        verify(jdbcTemplate, never()).update(anyString(), any(), any(), any());
    }
}
