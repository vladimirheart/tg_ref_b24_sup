package com.example.panel.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.service.BotProcessService;
import com.example.panel.service.BotRuntimeContractService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(BotProcessApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class BotProcessApiControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private BotProcessService botProcessService;

    @MockBean
    private ChannelRepository channelRepository;

    @Test
    void runtimeContractReturnsStructuredContractPayload() throws Exception {
        Channel channel = new Channel();
        channel.setId(51L);
        channel.setPlatform("telegram");

        when(channelRepository.findById(51L)).thenReturn(Optional.of(channel));
        when(botProcessService.describeRuntimeContract(channel)).thenReturn(
            new BotRuntimeContractService.BotRuntimeContract(
                51L,
                "telegram",
                "bot-telegram",
                "auto",
                "jar",
                "jar",
                "explicit-config",
                "C:/bots/bot-telegram.jar",
                List.of("APP_DB_TICKETS", "TELEGRAM_BOT_TOKEN"),
                List.of("HTTP_PROXY"),
                List.of(),
                new BotRuntimeContractService.BotReadinessContract(45000L, 250L, "Spring Boot started marker", "APPLICATION FAILED TO START banner"),
                new BotRuntimeContractService.BotProductionContract("jar", "C:/bots/dist/bot-telegram-runtime.jar", true, List.of()),
                new BotRuntimeContractService.BotLifecycleContract("running", "stopped", "error", "panel waits for readiness signal after process start", "panel terminates process when readiness is not confirmed in time")
            )
        );

        mockMvc.perform(get("/api/bots/51/runtime-contract"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.contract.botModule").value("bot-telegram"))
            .andExpect(jsonPath("$.contract.resolvedLauncherKind").value("jar"))
            .andExpect(jsonPath("$.contract.artifactSource").value("explicit-config"))
            .andExpect(jsonPath("$.contract.requiredEnvironmentKeys[0]").value("APP_DB_TICKETS"))
            .andExpect(jsonPath("$.contract.readiness.timeoutMillis").value(45000))
            .andExpect(jsonPath("$.contract.production.readyForProduction").value(true))
            .andExpect(jsonPath("$.contract.lifecycle.runningStatus").value("running"));
    }

    @Test
    void runtimeContractReturnsNotFoundForUnknownChannel() throws Exception {
        when(channelRepository.findById(404L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/bots/404/runtime-contract"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Канал не найден"));
    }
}
