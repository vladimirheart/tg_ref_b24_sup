package com.example.panel.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.service.BotProcessService;
import com.example.panel.service.BotRuntimeContractService;
import java.time.OffsetDateTime;
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
    void startReturnsStructuredStatusForExistingChannel() throws Exception {
        Channel channel = new Channel();
        channel.setId(11L);
        channel.setPlatform("telegram");

        when(channelRepository.findById(11L)).thenReturn(Optional.of(channel));
        when(botProcessService.start(channel)).thenReturn(
            new BotProcessService.BotProcessStatus(true, "running", OffsetDateTime.parse("2026-04-23T12:00:00Z"))
        );

        mockMvc.perform(post("/api/bots/11/start"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.status").value("running"))
            .andExpect(jsonPath("$.startedAt").value("2026-04-23T12:00:00Z"));
    }

    @Test
    void startReturnsErrorPayloadWhenServiceReportsStartupFailure() throws Exception {
        Channel channel = new Channel();
        channel.setId(15L);
        channel.setPlatform("telegram");

        when(channelRepository.findById(15L)).thenReturn(Optional.of(channel));
        when(botProcessService.start(channel)).thenReturn(
            new BotProcessService.BotProcessStatus(false, "Не удалось запустить бота: missing credential", null)
        );

        mockMvc.perform(post("/api/bots/15/start"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.status").value("Не удалось запустить бота: missing credential"))
            .andExpect(jsonPath("$.startedAt").isEmpty());
    }

    @Test
    void startReturnsNotFoundForUnknownChannel() throws Exception {
        when(channelRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(post("/api/bots/99/start"))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Канал не найден"));
    }

    @Test
    void stopReturnsServiceStatusPayload() throws Exception {
        when(botProcessService.stop(12L)).thenReturn(
            new BotProcessService.BotProcessStatus(false, "stopped", null)
        );

        mockMvc.perform(post("/api/bots/12/stop"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.status").value("stopped"))
            .andExpect(jsonPath("$.startedAt").doesNotExist());
    }

    @Test
    void stopReturnsErrorPayloadWhenServiceReportsFailure() throws Exception {
        when(botProcessService.stop(16L)).thenReturn(
            new BotProcessService.BotProcessStatus(false, "pid file is corrupted", null)
        );

        mockMvc.perform(post("/api/bots/16/stop"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.status").value("pid file is corrupted"))
            .andExpect(jsonPath("$.startedAt").doesNotExist());
    }

    @Test
    void statusReturnsCurrentProcessState() throws Exception {
        when(botProcessService.status(13L)).thenReturn(
            new BotProcessService.BotProcessStatus(true, "running", OffsetDateTime.parse("2026-04-23T12:30:00Z"))
        );

        mockMvc.perform(get("/api/bots/13/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.status").value("running"))
            .andExpect(jsonPath("$.startedAt").value("2026-04-23T12:30:00Z"));
    }

    @Test
    void statusReturnsStoppedPayloadWhenServiceReportsStopped() throws Exception {
        when(botProcessService.status(14L)).thenReturn(
            new BotProcessService.BotProcessStatus(false, "stopped", null)
        );

        mockMvc.perform(get("/api/bots/14/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.status").value("stopped"))
            .andExpect(jsonPath("$.startedAt").isEmpty());
    }

    @Test
    void statusReturnsErrorPayloadWhenServiceReportsFailure() throws Exception {
        when(botProcessService.status(17L)).thenReturn(
            new BotProcessService.BotProcessStatus(false, "startup log missing", null)
        );

        mockMvc.perform(get("/api/bots/17/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.status").value("startup log missing"))
            .andExpect(jsonPath("$.startedAt").isEmpty());
    }

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
    void runtimeContractReturnsStructuredContractPayloadForMaxChannel() throws Exception {
        Channel channel = new Channel();
        channel.setId(52L);
        channel.setPlatform("max");

        when(channelRepository.findById(52L)).thenReturn(Optional.of(channel));
        when(botProcessService.describeRuntimeContract(channel)).thenReturn(
            new BotRuntimeContractService.BotRuntimeContract(
                52L,
                "max",
                "bot-max",
                "auto",
                "jar",
                "jar",
                "explicit-config",
                "C:/bots/bot-max.jar",
                List.of("APP_DB_TICKETS", "MAX_BOT_TOKEN", "SERVER_PORT"),
                List.of("MAX_WEBHOOK_SECRET"),
                List.of(),
                new BotRuntimeContractService.BotReadinessContract(45000L, 250L, "Spring Boot started marker", "APPLICATION FAILED TO START banner"),
                new BotRuntimeContractService.BotProductionContract("jar", "C:/bots/dist/bot-max-runtime.jar", true, List.of()),
                new BotRuntimeContractService.BotLifecycleContract("running", "stopped", "error", "panel waits for readiness signal after process start", "panel terminates process when readiness is not confirmed in time")
            )
        );

        mockMvc.perform(get("/api/bots/52/runtime-contract"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.contract.botModule").value("bot-max"))
            .andExpect(jsonPath("$.contract.platform").value("max"))
            .andExpect(jsonPath("$.contract.requiredEnvironmentKeys[1]").value("MAX_BOT_TOKEN"))
            .andExpect(jsonPath("$.contract.optionalEnvironmentKeys[0]").value("MAX_WEBHOOK_SECRET"));
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
