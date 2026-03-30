package com.example.panel.controller;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.service.IntegrationNetworkService;
import com.example.panel.service.SharedConfigService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
}
