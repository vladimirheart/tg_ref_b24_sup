package com.example.panel.controller;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @MockBean
    private ObjectMapper objectMapper;

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
}
