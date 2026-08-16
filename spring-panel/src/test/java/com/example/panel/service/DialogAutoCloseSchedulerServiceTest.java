package com.example.panel.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.panel.config.PanelIntegrationTransportMode;
import com.example.panel.entity.Channel;
import com.example.panel.entity.Ticket;
import com.example.panel.entity.TicketActive;
import com.example.panel.entity.TicketId;
import com.example.panel.entity.TicketSpan;
import com.example.panel.repository.ChatHistoryRepository;
import com.example.panel.repository.TicketActiveRepository;
import com.example.panel.repository.TicketRepository;
import com.example.panel.repository.TicketSpanRepository;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.env.MockEnvironment;

class DialogAutoCloseSchedulerServiceTest {

    @Test
    void runAutoCloseSweepClosesStaleTicketAndPublishesSideEffects() {
        TicketActiveRepository ticketActiveRepository = mock(TicketActiveRepository.class);
        TicketRepository ticketRepository = mock(TicketRepository.class);
        TicketSpanRepository ticketSpanRepository = mock(TicketSpanRepository.class);
        ChatHistoryRepository chatHistoryRepository = mock(ChatHistoryRepository.class);
        UiEventOutboxAppendService uiEventOutboxAppendService = mock(UiEventOutboxAppendService.class);
        DialogAutoCloseFollowUpTaskService followUpTaskService = mock(DialogAutoCloseFollowUpTaskService.class);
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);

        TicketActive active = new TicketActive();
        active.setTicketId("T-900");
        active.setLastSeen(OffsetDateTime.now().minusHours(5));
        when(ticketActiveRepository.findAll()).thenReturn(java.util.List.of(active));

        Channel channel = new Channel();
        channel.setId(18L);
        channel.setAutoActionTemplateId("fast-close");

        Ticket ticket = new Ticket();
        TicketId ticketId = new TicketId();
        ticketId.setUserId(900L);
        ticketId.setTicketId("T-900");
        ticket.setId(ticketId);
        ticket.setChannel(channel);
        ticket.setStatus("open");
        ticket.setClosedCount(0);
        ticket.setWorkTimeTotalSec(15L);
        when(ticketRepository.findByIdTicketId("T-900")).thenReturn(Optional.of(ticket));

        TicketSpan span = new TicketSpan();
        span.setTicketId("T-900");
        span.setSpanNo(2);
        span.setStartedAt(OffsetDateTime.now().minusHours(6));
        when(ticketSpanRepository.findFirstByTicketIdAndEndedAtIsNullOrderBySpanNoDesc("T-900"))
            .thenReturn(Optional.of(span));

        DialogAutoCloseSchedulerService service = new DialogAutoCloseSchedulerService(
            ticketActiveRepository,
            ticketRepository,
            ticketSpanRepository,
            chatHistoryRepository,
            mock(SharedConfigService.class),
            uiEventOutboxAppendService,
            followUpTaskService,
            new PanelIntegrationTransportMode(new MockEnvironment().withProperty("app.integration.transport.mode", "rabbitmq")),
            jdbcTemplate
        );

        DialogAutoCloseSchedulerService.AutoCloseRunResult result = service.runAutoCloseSweep(Map.of(
            "auto_close_config", Map.of(
                "templates", java.util.List.of(Map.of("id", "fast-close", "hours", 1)),
                "active_template_id", "fast-close"
            )
        ));

        assertThat(result.checkedTickets()).isEqualTo(1);
        assertThat(result.closedTickets()).isEqualTo(1);
        assertThat(ticket.getStatus()).isEqualTo("closed");
        assertThat(ticket.getResolvedBy()).isEqualTo("auto_close");
        assertThat(ticket.getResolvedAt()).isNotNull();
        assertThat(ticket.getClosedCount()).isEqualTo(1);
        assertThat(ticket.getWorkTimeTotalSec()).isGreaterThan(15L);

        verify(ticketRepository).save(ticket);
        verify(ticketActiveRepository).deleteById("T-900");
        verify(chatHistoryRepository).save(any());
        verify(uiEventOutboxAppendService).publishTicketClosed(eq("T-900"), eq(18L), any(), eq(true));
        verify(followUpTaskService).createTaskForAutoClosedDialog("T-900");
        verify(jdbcTemplate).update(eq("""
                UPDATE pending_feedback_requests
                   SET expires_at = ?, source = ?
                 WHERE ticket_id = ?
                """), any(), eq("auto_close"), eq("T-900"));
    }

    @Test
    void runAutoCloseSweepSkipsTicketWhenTemplateDisablesAutoClose() {
        TicketActiveRepository ticketActiveRepository = mock(TicketActiveRepository.class);
        TicketRepository ticketRepository = mock(TicketRepository.class);
        TicketSpanRepository ticketSpanRepository = mock(TicketSpanRepository.class);
        ChatHistoryRepository chatHistoryRepository = mock(ChatHistoryRepository.class);

        TicketActive active = new TicketActive();
        active.setTicketId("T-901");
        active.setLastSeen(OffsetDateTime.now().minusHours(10));
        when(ticketActiveRepository.findAll()).thenReturn(java.util.List.of(active));

        Channel channel = new Channel();
        channel.setAutoActionTemplateId("disabled-template");
        Ticket ticket = new Ticket();
        TicketId ticketId = new TicketId();
        ticketId.setUserId(901L);
        ticketId.setTicketId("T-901");
        ticket.setId(ticketId);
        ticket.setChannel(channel);
        ticket.setStatus("open");
        when(ticketRepository.findByIdTicketId("T-901")).thenReturn(Optional.of(ticket));

        DialogAutoCloseSchedulerService service = new DialogAutoCloseSchedulerService(
            ticketActiveRepository,
            ticketRepository,
            ticketSpanRepository,
            chatHistoryRepository,
            mock(SharedConfigService.class),
            mock(UiEventOutboxAppendService.class),
            mock(DialogAutoCloseFollowUpTaskService.class),
            new PanelIntegrationTransportMode(new MockEnvironment().withProperty("app.integration.transport.mode", "rabbitmq")),
            mock(JdbcTemplate.class)
        );

        DialogAutoCloseSchedulerService.AutoCloseRunResult result = service.runAutoCloseSweep(Map.of(
            "auto_close_config", Map.of(
                "templates", java.util.List.of(Map.of("id", "disabled-template", "hours", 0)),
                "active_template_id", "disabled-template"
            )
        ));

        assertThat(result.checkedTickets()).isEqualTo(1);
        assertThat(result.closedTickets()).isZero();
        verify(ticketRepository, never()).save(any());
        verify(ticketActiveRepository, never()).deleteById("T-901");
    }
}
