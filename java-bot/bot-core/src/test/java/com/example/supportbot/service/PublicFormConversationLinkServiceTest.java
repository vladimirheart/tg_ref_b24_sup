package com.example.supportbot.service;

import com.example.supportbot.entity.Channel;
import com.example.supportbot.entity.Ticket;
import com.example.supportbot.entity.TicketActive;
import com.example.supportbot.entity.TicketId;
import com.example.supportbot.repository.TicketActiveRepository;
import com.example.supportbot.repository.TicketRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PublicFormConversationLinkServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    @Mock
    private TicketRepository ticketRepository;

    @Mock
    private TicketActiveRepository ticketActiveRepository;

    @Mock
    private ChatHistoryService chatHistoryService;

    @InjectMocks
    private PublicFormConversationLinkService service;

    @Test
    void bindSessionToChannelRebindsTicketToBotChannelAndStoresActiveIdentity() throws Exception {
        Channel channel = new Channel();
        channel.setId(77L);
        channel.setPlatform("max");

        Ticket ticket = new Ticket();
        TicketId ticketId = new TicketId();
        ticketId.setTicketId("WEB-1001");
        ticketId.setUserId(900000001L);
        ticket.setId(ticketId);
        ticket.setStatus("open");

        doAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            RowMapper<Object> rowMapper = (RowMapper<Object>) invocation.getArgument(1);
            ResultSet rs = org.mockito.Mockito.mock(ResultSet.class);
            when(rs.getLong("id")).thenReturn(15L);
            when(rs.getString("ticket_id")).thenReturn("WEB-1001");
            when(rs.getLong("user_id")).thenReturn(900000001L);
            when(rs.getString("client_name")).thenReturn("Ирина");
            return List.of(rowMapper.mapRow(rs, 0));
        }).when(jdbcTemplate).query(anyString(), any(RowMapper.class), eq("continue-token"));

        when(ticketRepository.findByIdTicketId("WEB-1001")).thenReturn(Optional.of(ticket));
        when(ticketActiveRepository.findById("WEB-1001")).thenReturn(Optional.empty());
        when(ticketRepository.save(any(Ticket.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(ticketActiveRepository.save(any(TicketActive.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PublicFormConversationLinkService.LinkResult result =
                service.bindSessionToChannel("continue-token", 445566L, "max-user", channel);

        assertThat(result.success()).isTrue();
        assertThat(result.ticketId()).isEqualTo("WEB-1001");
        assertThat(result.closed()).isFalse();
        assertThat(result.clientName()).isEqualTo("Ирина");
        assertThat(ticket.getChannel()).isSameAs(channel);

        verify(jdbcTemplate).update(anyString(), eq(445566L), eq("max-user"), eq(77L), any(), eq("public_form_continue"), eq("WEB-1001"));
        verify(jdbcTemplate).update(anyString(), eq(445566L), eq("max-user"), any(), eq(15L));

        ArgumentCaptor<TicketActive> activeCaptor = ArgumentCaptor.forClass(TicketActive.class);
        verify(ticketActiveRepository).save(activeCaptor.capture());
        assertThat(activeCaptor.getValue().getTicketId()).isEqualTo("WEB-1001");
        assertThat(activeCaptor.getValue().getUser()).isEqualTo("max-user");
        assertThat(activeCaptor.getValue().getLastSeen()).isNotNull();

        verify(chatHistoryService).storeSystemEvent(445566L, "WEB-1001", channel,
                "Клиент продолжил диалог через MAX.");
    }

    @Test
    void bindSessionToChannelRejectsUnknownTokenWithoutSideEffects() {
        Channel channel = new Channel();
        channel.setId(11L);
        channel.setPlatform("vk");
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), eq("missing-token"))).thenReturn(List.of());

        PublicFormConversationLinkService.LinkResult result =
                service.bindSessionToChannel("missing-token", 1122L, "vk-user", channel);

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("не найден");
        verify(ticketRepository, never()).findByIdTicketId(anyString());
        verify(ticketRepository, never()).save(any(Ticket.class));
        verify(ticketActiveRepository, never()).save(any(TicketActive.class));
        verify(chatHistoryService, never()).storeSystemEvent(any(), any(), any(), any());
    }
}
