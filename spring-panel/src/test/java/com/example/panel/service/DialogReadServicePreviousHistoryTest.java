package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

class DialogReadServicePreviousHistoryTest {

    @Test
    void noPreviousPageReturnsNullableBatchAndKeepsExactTicketIdentity() {
        DialogConversationReadService conversation = mock(DialogConversationReadService.class);
        when(conversation.loadPreviousDialogHistory(" T/100 ", 0)).thenReturn(Optional.empty());
        DialogReadService service = new DialogReadService(
                mock(DialogDetailsReadService.class),
                mock(DialogResponsibilityService.class),
                conversation,
                mock(DialogParticipantService.class));

        ResponseEntity<?> response = service.loadPreviousHistory(" T/100 ", -4);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isInstanceOf(Map.class);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.containsKey("batch")).isTrue();
        assertThat(body.get("batch")).isNull();
        assertThat(body.get("has_more")).isEqualTo(false);
        assertThat(body.get("next_offset")).isEqualTo(0);
        verify(conversation).loadPreviousDialogHistory(" T/100 ", 0);
    }
}
