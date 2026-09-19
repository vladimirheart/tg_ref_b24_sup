package com.example.panel.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.config.RuntimeCoordinationProperties;
import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeRoleProperties;
import com.example.panel.security.InternalBotApiProperties;
import com.example.panel.security.InternalBotApiRequestGuardService;
import com.example.panel.service.BotProcessService;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BotRunnerLifecycleInternalController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({InternalBotApiRequestGuardService.class, InternalBotApiProperties.class, RuntimeCoordinationProperties.class})
@TestPropertySource(properties = "app.bots.internal-api.token=test-internal-token")
class BotRunnerLifecycleInternalControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BotProcessService botProcessService;

    @MockBean
    private ChannelRepository channelRepository;

    @MockBean
    private RuntimeRoleProperties runtimeRoleProperties;

    @Test
    void startRequiresInternalToken() throws Exception {
        mockMvc.perform(post("/internal/api/bot/runtime/11/start")
                .header(InternalBotApiRequestGuardService.IDEMPOTENCY_HEADER, "cmd-11"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void webRoleCannotExecuteLifecycleCommandLocally() throws Exception {
        when(runtimeRoleProperties.resolvedRole()).thenReturn(RuntimeRole.WEB);

        mockMvc.perform(post("/internal/api/bot/runtime/11/start")
                .header(InternalBotApiRequestGuardService.AUTH_HEADER, "test-internal-token")
                .header(InternalBotApiRequestGuardService.IDEMPOTENCY_HEADER, "cmd-11"))
            .andExpect(status().isConflict());
    }

    @Test
    void runnerStartReturnsCommandAcknowledgement() throws Exception {
        Channel channel = new Channel();
        channel.setId(11L);
        when(runtimeRoleProperties.resolvedRole()).thenReturn(RuntimeRole.BOT_RUNNER);
        when(runtimeRoleProperties.resolvedInstanceId()).thenReturn("runner-test");
        when(channelRepository.findById(11L)).thenReturn(Optional.of(channel));
        when(botProcessService.start(channel)).thenReturn(
            new BotProcessService.BotProcessStatus(true, "running", OffsetDateTime.parse("2026-09-19T15:00:00Z"))
        );

        mockMvc.perform(post("/internal/api/bot/runtime/11/start")
                .header(InternalBotApiRequestGuardService.AUTH_HEADER, "test-internal-token")
                .header(InternalBotApiRequestGuardService.IDEMPOTENCY_HEADER, "cmd-11"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.commandId").value("cmd-11"))
            .andExpect(jsonPath("$.channelId").value(11))
            .andExpect(jsonPath("$.action").value("start"))
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.status").value("running"))
            .andExpect(jsonPath("$.runnerInstanceId").value("runner-test"));

        verify(botProcessService).start(channel);
    }

    @Test
    void runnerStopReturnsConfirmedStoppedStatus() throws Exception {
        when(runtimeRoleProperties.resolvedRole()).thenReturn(RuntimeRole.BOT_RUNNER);
        when(runtimeRoleProperties.resolvedInstanceId()).thenReturn("runner-test");
        when(botProcessService.stop(12L)).thenReturn(
            new BotProcessService.BotProcessStatus(false, "stopped", null)
        );

        mockMvc.perform(post("/internal/api/bot/runtime/12/stop")
                .header(InternalBotApiRequestGuardService.AUTH_HEADER, "test-internal-token")
                .header(InternalBotApiRequestGuardService.IDEMPOTENCY_HEADER, "cmd-12"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.commandId").value("cmd-12"))
            .andExpect(jsonPath("$.action").value("stop"))
            .andExpect(jsonPath("$.status").value("stopped"))
            .andExpect(jsonPath("$.runnerInstanceId").value("runner-test"));

        verify(botProcessService).stop(12L);
    }
}
