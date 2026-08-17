package com.example.panel.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.entity.Channel;
import com.example.panel.service.BotRuntimeBlacklistService;
import com.example.panel.service.BotRuntimeChannelService;
import com.example.panel.service.BotRuntimeTicketWriteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BotRuntimeWriteApiController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "app.bots.internal-api.token=test-internal-token")
class BotRuntimeWriteApiControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BotRuntimeTicketWriteService ticketWriteService;

    @MockBean
    private BotRuntimeChannelService channelService;

    @MockBean
    private BotRuntimeBlacklistService blacklistService;

    @Test
    void registerActivityRequiresInternalToken() throws Exception {
        mockMvc.perform(put("/internal/api/bot/tickets/T-100/activity")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"userIdentity":"operator"}
                        """))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void reopenTicketDelegatesToWriteService() throws Exception {
        when(ticketWriteService.reopenTicket("T-200"))
            .thenReturn(new BotRuntimeTicketWriteService.MutationResult(true, true));

        mockMvc.perform(post("/internal/api/bot/tickets/T-200/reopen")
                .header("X-Iguana-Bot-Api-Token", "test-internal-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updated").value(true))
            .andExpect(jsonPath("$.exists").value(true));
    }

    @Test
    void operatorRelayDelegatesPayloadToWriteService() throws Exception {
        when(ticketWriteService.recordOperatorRelay("T-300", "Reply text", 7001L, 6999L, "operator"))
            .thenReturn(new BotRuntimeTicketWriteService.MutationResult(true, true));

        mockMvc.perform(post("/internal/api/bot/tickets/T-300/operator-relay")
                .header("X-Iguana-Bot-Api-Token", "test-internal-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "message": "Reply text",
                          "telegramMessageId": 7001,
                          "replyToTelegramId": 6999,
                          "operatorIdentity": "operator"
                        }
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updated").value(true))
            .andExpect(jsonPath("$.exists").value(true));

        verify(ticketWriteService).recordOperatorRelay("T-300", "Reply text", 7001L, 6999L, "operator");
    }

    @Test
    void clientMessageEditDelegatesPayloadToWriteService() throws Exception {
        when(ticketWriteService.markClientMessageEdited(15L, 7010L, "Edited text"))
            .thenReturn(new BotRuntimeTicketWriteService.MutationResult(true, true));

        mockMvc.perform(put("/internal/api/bot/channels/15/messages/7010/client-edit")
                .header("X-Iguana-Bot-Api-Token", "test-internal-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "message": "Edited text"
                        }
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updated").value(true))
            .andExpect(jsonPath("$.exists").value(true));

        verify(ticketWriteService).markClientMessageEdited(15L, 7010L, "Edited text");
    }

    @Test
    void feedbackSubmitDelegatesPayloadToWriteService() throws Exception {
        when(ticketWriteService.storeFeedback(902L, 5))
            .thenReturn(new BotRuntimeTicketWriteService.MutationResult(true, true));

        mockMvc.perform(post("/internal/api/bot/feedback/pending/902/submit")
                .header("X-Iguana-Bot-Api-Token", "test-internal-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "rating": 5
                        }
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updated").value(true))
            .andExpect(jsonPath("$.exists").value(true));

        verify(ticketWriteService).storeFeedback(902L, 5);
    }

    @Test
    void resolveChannelDelegatesPayloadToChannelService() throws Exception {
        Channel channel = new Channel();
        channel.setId(51L);
        channel.setToken("bot-token");
        channel.setChannelName("Telegram");
        channel.setPlatform("telegram");
        channel.setPublicId("public-51");
        when(channelService.resolveConfiguredChannel(51L, "bot-token", "Telegram", "telegram")).thenReturn(channel);

        mockMvc.perform(post("/internal/api/bot/channels/resolve")
                .header("X-Iguana-Bot-Api-Token", "test-internal-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "channelId": 51,
                          "token": "bot-token",
                          "channelName": "Telegram",
                          "platform": "telegram"
                        }
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(51L))
            .andExpect(jsonPath("$.publicId").value("public-51"));

        verify(channelService).resolveConfiguredChannel(51L, "bot-token", "Telegram", "telegram");
    }

    @Test
    void updateSupportChatDelegatesPayloadToChannelService() throws Exception {
        Channel channel = new Channel();
        channel.setId(52L);
        channel.setSupportChatId("-10052");
        when(channelService.updateSupportChatId(52L, "-10052")).thenReturn(channel);

        mockMvc.perform(put("/internal/api/bot/channels/52/support-chat")
                .header("X-Iguana-Bot-Api-Token", "test-internal-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "supportChatId": "-10052"
                        }
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(52L))
            .andExpect(jsonPath("$.supportChatId").value("-10052"));

        verify(channelService).updateSupportChatId(52L, "-10052");
    }

    @Test
    void clearActivityDelegatesToWriteService() throws Exception {
        when(ticketWriteService.clearActivity("T-400"))
            .thenReturn(new BotRuntimeTicketWriteService.MutationResult(false, true));

        mockMvc.perform(delete("/internal/api/bot/tickets/T-400/activity")
                .header("X-Iguana-Bot-Api-Token", "test-internal-token"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.updated").value(false))
            .andExpect(jsonPath("$.exists").value(true));
    }

    @Test
    void unblockRequestDelegatesPayloadToBlacklistService() throws Exception {
        when(blacklistService.requestUnblock(77L, "", 12L, java.time.Duration.ofMinutes(15)))
            .thenReturn(new BotRuntimeBlacklistService.UnblockRequestDecisionLookup(
                new BotRuntimeBlacklistService.PendingUnblockRequestLookup(
                    901L,
                    "77",
                    12L,
                    "",
                    java.time.OffsetDateTime.parse("2026-08-17T10:00:00Z"),
                    "pending"
                ),
                true,
                java.time.Duration.ZERO
            ));

        mockMvc.perform(post("/internal/api/bot/blacklist/unblock-requests")
                .header("X-Iguana-Bot-Api-Token", "test-internal-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "userId": 77,
                          "reason": "",
                          "channelId": 12,
                          "cooldownSeconds": 900
                        }
                        """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.created").value(true))
            .andExpect(jsonPath("$.request.id").value(901L))
            .andExpect(jsonPath("$.request.userId").value("77"));

        verify(blacklistService).requestUnblock(77L, "", 12L, java.time.Duration.ofMinutes(15));
    }
}
