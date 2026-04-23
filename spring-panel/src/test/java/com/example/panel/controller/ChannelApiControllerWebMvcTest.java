package com.example.panel.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.entity.Channel;
import com.example.panel.model.channel.BotCredential;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.service.IntegrationNetworkService;
import com.example.panel.service.SharedConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(ChannelApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class ChannelApiControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChannelRepository channelRepository;

    @MockBean
    private SharedConfigService sharedConfigService;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private IntegrationNetworkService integrationNetworkService;

    @Test
    void regeneratePublicIdSupportsChannelsRouteAlias() throws Exception {
        Channel channel = new Channel();
        channel.setId(55L);
        channel.setPublicId("old_public_id");

        when(channelRepository.findById(55L)).thenReturn(Optional.of(channel));
        when(channelRepository.save(any(Channel.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(sharedConfigService.loadBotCredentials()).thenReturn(List.of());

        mockMvc.perform(post("/api/channels/55/public-id/regenerate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.public_id").isNotEmpty())
                .andExpect(jsonPath("$.channel.id").value(55));
    }

    @Test
    void createChannelPersistsVkPlatformConfigAndTemplateSelections() throws Exception {
        when(channelRepository.save(any(Channel.class))).thenAnswer(invocation -> {
            Channel channel = invocation.getArgument(0);
            channel.setId(42L);
            return channel;
        });
        when(sharedConfigService.loadBotCredentials()).thenReturn(List.of());

        mockMvc.perform(post("/api/channels")
                        .contentType("application/json")
                        .content("""
                                {
                                  "channel_name": "VK Support",
                                  "platform": "vk",
                                  "token": "vk-access-token",
                                  "question_template_id": "q-template",
                                  "rating_template_id": "r-template",
                                  "auto_action_template_id": "auto-template",
                                  "platform_config": {
                                    "group_id": 123,
                                    "confirmation_token": "vk-confirm",
                                    "secret": "vk-secret"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.channel.id").value(42))
                .andExpect(jsonPath("$.channel.platform").value("vk"))
                .andExpect(jsonPath("$.channel.public_id").isNotEmpty())
                .andExpect(jsonPath("$.channel.question_template_id").value("q-template"))
                .andExpect(jsonPath("$.channel.rating_template_id").value("r-template"))
                .andExpect(jsonPath("$.channel.auto_action_template_id").value("auto-template"));

        verify(channelRepository).save(argThat(channel ->
                "VK Support".equals(channel.getChannelName())
                        && "vk".equals(channel.getPlatform())
                        && channel.getPublicId() != null
                        && !channel.getPublicId().isBlank()
                        && "q-template".equals(channel.getQuestionTemplateId())
                        && "r-template".equals(channel.getRatingTemplateId())
                        && "auto-template".equals(channel.getAutoActionTemplateId())
                        && "{\"group_id\":123,\"confirmation_token\":\"vk-confirm\",\"secret\":\"vk-secret\"}".equals(channel.getPlatformConfig())
        ));
    }

    @Test
    void getBotCredentialsReturnsSharedConfigCredentials() throws Exception {
        when(sharedConfigService.loadBotCredentials()).thenReturn(List.of(
            new BotCredential(5L, "TG Main", "telegram", "123456:ABCDEF", true)
        ));

        mockMvc.perform(get("/api/bot-credentials"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.credentials[0].id").value(5))
            .andExpect(jsonPath("$.credentials[0].name").value("TG Main"))
            .andExpect(jsonPath("$.credentials[0].platform").value("telegram"))
            .andExpect(jsonPath("$.credentials[0].is_active").value(true))
            .andExpect(jsonPath("$.credentials[0].masked_token").exists());
    }

    @Test
    void createBotCredentialPersistsExpandedSharedConfigCredentialList() throws Exception {
        List<BotCredential> existing = new ArrayList<>();
        existing.add(new BotCredential(2L, "Legacy", "telegram", "legacy-token", true));
        when(sharedConfigService.loadBotCredentials()).thenReturn(existing);

        mockMvc.perform(post("/api/bot-credentials")
                .contentType("application/json")
                .content("""
                    {
                      "name": "VK Main",
                      "platform": "vk",
                      "token": "vk-token",
                      "is_active": false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.credential.id").value(3))
            .andExpect(jsonPath("$.credential.name").value("VK Main"))
            .andExpect(jsonPath("$.credential.platform").value("vk"))
            .andExpect(jsonPath("$.credential.is_active").value(false));

        verify(sharedConfigService).saveBotCredentials(argThat(credentials ->
            credentials.size() == 2
                && credentials.stream().anyMatch(item -> item.id() == 2L && "Legacy".equals(item.name()))
                && credentials.stream().anyMatch(item -> item.id() == 3L && "VK Main".equals(item.name()) && "vk".equals(item.platform()) && Boolean.FALSE.equals(item.active()))
        ));
    }

    @Test
    void deleteBotCredentialClearsLinkedChannelsAndPersistsTrimmedCredentialList() throws Exception {
        when(sharedConfigService.loadBotCredentials()).thenReturn(List.of(
            new BotCredential(7L, "TG Main", "telegram", "token-1", true),
            new BotCredential(8L, "VK Backup", "vk", "token-2", false)
        ));
        Channel linkedChannel = new Channel();
        linkedChannel.setId(101L);
        linkedChannel.setCredentialId(8L);
        when(channelRepository.findAll()).thenReturn(List.of(linkedChannel));

        mockMvc.perform(delete("/api/bot-credentials/8"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));

        verify(sharedConfigService).saveBotCredentials(argThat(credentials ->
            credentials.size() == 1 && credentials.get(0).id() == 7L
        ));
        verify(channelRepository).saveAll(argThat(channels ->
            channels.iterator().hasNext() && channels.iterator().next().getCredentialId() == null
        ));
    }
}
