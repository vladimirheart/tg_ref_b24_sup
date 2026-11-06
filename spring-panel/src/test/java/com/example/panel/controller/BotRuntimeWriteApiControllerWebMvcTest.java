package com.example.panel.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.entity.Channel;
import com.example.panel.config.RuntimeCoordinationProperties;
import com.example.panel.security.InternalBotApiProperties;
import com.example.panel.security.InternalBotApiRequestGuardService;
import com.example.panel.service.BotRuntimeBlacklistService;
import com.example.panel.service.BotRuntimeChannelService;
import com.example.panel.service.BotRuntimeTicketWriteService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BotRuntimeWriteApiController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({InternalBotApiRequestGuardService.class, InternalBotApiProperties.class, RuntimeCoordinationProperties.class})
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
    void reopenTicketRetiredToWriteService() throws Exception {
        mockMvc.perform(post("/internal/api/bot/tickets/T-200/reopen")
                .header("X-Iguana-Bot-Api-Token", "test-internal-token")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isGone());
        org.mockito.Mockito.verifyNoInteractions(ticketWriteService, channelService);
    }

    @Test
    void reopenTicketWithUnverifiedOperatorRetiredPayloadToWriteService() throws Exception {
        mockMvc.perform(post("/internal/api/bot/tickets/T-201/reopen")
                .header("X-Iguana-Bot-Api-Token", "test-internal-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"userIdentity\":\"operator\"}"))
            .andExpect(status().isGone());
        org.mockito.Mockito.verifyNoInteractions(ticketWriteService, channelService);
    }

    @Test
    void operatorRelayRetiredPayloadToWriteService() throws Exception {
        mockMvc.perform(post("/internal/api/bot/tickets/T-300/operator-relay")
                .header("X-Iguana-Bot-Api-Token", "test-internal-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"text\",\"operatorIdentity\":\"admin\"}"))
            .andExpect(status().isGone());
        org.mockito.Mockito.verifyNoInteractions(ticketWriteService, channelService);
    }

    @Test
    void clientMessageEditWithoutAuthorIsRetired() throws Exception {
        mockMvc.perform(put("/internal/api/bot/channels/15/messages/7010/client-edit")
                .header("X-Iguana-Bot-Api-Token", "test-internal-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"Edited text\"}"))
            .andExpect(status().isGone());
        org.mockito.Mockito.verifyNoInteractions(ticketWriteService);
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"telegram", "vk", "max"})
    void ownedClientEditPreservesAuthorAndPlatform(String platform) throws Exception {
        when(ticketWriteService.markClientMessageEdited(platform, 9007199254740995L, 9007199254740993L,
            Long.MAX_VALUE, "Edited text")).thenReturn(new BotRuntimeTicketWriteService.MutationResult(true, true));
        mockMvc.perform(put("/internal/api/bot/channels/9007199254740995/users/9007199254740993/messages/9223372036854775807/client-edit")
                .header("X-Iguana-Bot-Api-Token", "test-internal-token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"Edited text\",\"platform\":\"" + platform + "\"}"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.updated").value(true));
        verify(ticketWriteService).markClientMessageEdited(platform, 9007199254740995L, 9007199254740993L,
            Long.MAX_VALUE, "Edited text");
    }

    @Test
    void operatorMessageEditRetiredPayloadToWriteService() throws Exception {
        mockMvc.perform(put("/internal/api/bot/tickets/T-301/operator-messages/7011")
                .header("X-Iguana-Bot-Api-Token", "test-internal-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"text\",\"operatorIdentity\":\"admin\"}"))
            .andExpect(status().isGone());
        org.mockito.Mockito.verifyNoInteractions(ticketWriteService, channelService);
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
    void updateSupportChatRetiredPayloadToChannelService() throws Exception {
        mockMvc.perform(put("/internal/api/bot/channels/15/support-chat")
                .header("X-Iguana-Bot-Api-Token", "test-internal-token")
                .contentType(MediaType.APPLICATION_JSON).content("{\"supportChatId\":\"-100\"}"))
            .andExpect(status().isGone());
        org.mockito.Mockito.verifyNoInteractions(ticketWriteService, channelService);
    }

    @Test
    void clearActivityRetiredToWriteService() throws Exception {
        mockMvc.perform(delete("/internal/api/bot/tickets/T-400/activity")
                .header("X-Iguana-Bot-Api-Token", "test-internal-token")
                .contentType(MediaType.APPLICATION_JSON).content("{}"))
            .andExpect(status().isGone());
        org.mockito.Mockito.verifyNoInteractions(ticketWriteService, channelService);
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
