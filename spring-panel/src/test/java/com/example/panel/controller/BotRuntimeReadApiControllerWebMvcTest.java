package com.example.panel.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.entity.Channel;
import com.example.panel.service.BotRuntimeChannelService;
import com.example.panel.service.BotRuntimeConfigService;
import com.example.panel.service.BotRuntimeTicketReadService;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BotRuntimeReadApiController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "app.bots.internal-api.token=test-internal-token")
class BotRuntimeReadApiControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BotRuntimeTicketReadService ticketReadService;

    @MockBean
    private BotRuntimeChannelService channelService;

    @MockBean
    private BotRuntimeConfigService runtimeConfigService;

    @Test
    void activeTicketReturnsLookupForAuthorizedInternalRequest() throws Exception {
        when(ticketReadService.findActiveTicket(101L, "tg-101", 17L)).thenReturn(Optional.of(
            new BotRuntimeTicketReadService.ActiveTicketLookup(
                "T-101",
                "tg-101",
                OffsetDateTime.parse("2026-08-16T09:00:00Z")
            )
        ));

        mockMvc.perform(get("/internal/api/bot/tickets/active")
                .header("X-Iguana-Bot-Api-Token", "test-internal-token")
                .param("userId", "101")
                .param("username", "tg-101")
                .param("channelId", "17"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ticketId").value("T-101"))
            .andExpect(jsonPath("$.userIdentity").value("tg-101"))
            .andExpect(jsonPath("$.lastSeen").value("2026-08-16T09:00:00Z"));
    }

    @Test
    void recentTicketsRejectsUnauthorizedInternalRequest() throws Exception {
        mockMvc.perform(get("/internal/api/bot/users/101/tickets/recent"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void lastTicketContextReturnsPayloadForAuthorizedInternalRequest() throws Exception {
        when(ticketReadService.findLastTicketContext(77L)).thenReturn(Optional.of(
            new BotRuntimeTicketReadService.LastTicketContext(
                "T-77",
                "Retail",
                "store",
                "Moscow",
                "Tverskaya",
                "Internet down",
                OffsetDateTime.parse("2026-08-16T10:15:00Z"),
                LocalDate.parse("2026-08-16"),
                7001L
            )
        ));

        mockMvc.perform(get("/internal/api/bot/users/77/last-ticket-context")
                .header("X-Iguana-Bot-Api-Token", "test-internal-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.ticketId").value("T-77"))
            .andExpect(jsonPath("$.business").value("Retail"))
            .andExpect(jsonPath("$.messageId").value(7001L));
    }

    @Test
    void recentTicketsReturnsListForAuthorizedInternalRequest() throws Exception {
        when(ticketReadService.findRecentTickets(77L, 2)).thenReturn(List.of(
            new BotRuntimeTicketReadService.TicketSummaryLookup(
                "T-1",
                "20260816-001",
                "Internet down",
                "Retail",
                "store",
                "Moscow",
                "Tverskaya",
                5,
                OffsetDateTime.parse("2026-08-16T10:15:00Z")
            )
        ));

        mockMvc.perform(get("/internal/api/bot/users/77/tickets/recent")
                .header("X-Iguana-Bot-Api-Token", "test-internal-token")
                .param("limit", "2"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].ticketId").value("T-1"))
            .andExpect(jsonPath("$[0].requestNumber").value("20260816-001"));
    }

    @Test
    void pendingFeedbackRequestReturnsLookupForAuthorizedInternalRequest() throws Exception {
        when(ticketReadService.findActiveFeedbackRequest(55L, 12L)).thenReturn(Optional.of(
            new BotRuntimeTicketReadService.PendingFeedbackRequestLookup(
                901L,
                55L,
                12L,
                "T-901",
                "user_prompt",
                OffsetDateTime.parse("2026-08-17T09:00:00Z")
            )
        ));

        mockMvc.perform(get("/internal/api/bot/users/55/feedback/pending")
                .header("X-Iguana-Bot-Api-Token", "test-internal-token")
                .param("channelId", "12"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(901L))
            .andExpect(jsonPath("$.ticketId").value("T-901"))
            .andExpect(jsonPath("$.channelId").value(12L));
    }

    @Test
    void channelReturnsLookupForAuthorizedInternalRequest() throws Exception {
        Channel channel = new Channel();
        channel.setId(52L);
        channel.setToken("bot-token");
        channel.setPlatform("telegram");
        channel.setSupportChatId("-10052");
        when(channelService.findChannel(52L)).thenReturn(Optional.of(channel));

        mockMvc.perform(get("/internal/api/bot/channels/52")
                .header("X-Iguana-Bot-Api-Token", "test-internal-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(52L))
            .andExpect(jsonPath("$.platform").value("telegram"))
            .andExpect(jsonPath("$.supportChatId").value("-10052"));
    }

    @Test
    void runtimeConfigReturnsLookupForAuthorizedInternalRequest() throws Exception {
        when(runtimeConfigService.findRuntimeConfig(52L)).thenReturn(Optional.of(
            new BotRuntimeConfigService.RuntimeConfigLookup(
                52L,
                java.util.Map.of("active_template_id", "q-52"),
                java.util.Map.of("Retail", java.util.Map.of()),
                java.util.Map.of("locations", java.util.Map.of("label", "Структура локаций"))
            )
        ));

        mockMvc.perform(get("/internal/api/bot/channels/52/runtime-config")
                .header("X-Iguana-Bot-Api-Token", "test-internal-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.channelId").value(52L))
            .andExpect(jsonPath("$.botSettings.active_template_id").value("q-52"))
            .andExpect(jsonPath("$.presetDefinitions.locations.label").value("Структура локаций"));
    }
}
