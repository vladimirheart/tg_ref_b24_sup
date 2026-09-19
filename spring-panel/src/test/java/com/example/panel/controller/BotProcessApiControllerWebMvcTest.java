package com.example.panel.controller;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.runtime.RuntimeRoleProperties;
import com.example.panel.service.BotLifecycleCommandResult;
import com.example.panel.service.BotProcessService;
import com.example.panel.service.BotRunnerLifecycleClient;
import com.example.panel.service.BotRuntimeContractService;
import com.example.panel.service.UiEventStreamService;
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
    private BotRunnerLifecycleClient botRunnerLifecycleClient;

    @MockBean
    private ChannelRepository channelRepository;

    @MockBean
    private UiEventStreamService uiEventStreamService;

    @MockBean
    private RuntimeRoleProperties runtimeRoleProperties;

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
    void startReturnsUnknownStatusWhenServiceReturnsNull() throws Exception {
        Channel channel = new Channel();
        channel.setId(18L);
        channel.setPlatform("telegram");

        when(channelRepository.findById(18L)).thenReturn(Optional.of(channel));
        when(botProcessService.start(channel)).thenReturn(null);

        mockMvc.perform(post("/api/bots/18/start"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.status").value("unknown"))
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
    void stopReturnsUnknownStatusWhenServiceReturnsNull() throws Exception {
        when(botProcessService.stop(19L)).thenReturn(null);

        mockMvc.perform(post("/api/bots/19/stop"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.status").value("unknown"))
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
    void statusTreatsStoppedCaseInsensitivelyAsSuccess() throws Exception {
        when(botProcessService.status(60L)).thenReturn(
            new BotProcessService.BotProcessStatus(false, "STOPPED", null)
        );

        mockMvc.perform(get("/api/bots/60/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.status").value("STOPPED"));
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
    void statusReturnsUnknownPayloadWhenServiceReturnsNull() throws Exception {
        when(botProcessService.status(20L)).thenReturn(null);

        mockMvc.perform(get("/api/bots/20/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.status").value("unknown"))
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
                List.of("APP_DB_PANEL_RUNTIME", "TELEGRAM_BOT_TOKEN"),
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
            .andExpect(jsonPath("$.contract.requiredEnvironmentKeys[0]").value("APP_DB_PANEL_RUNTIME"))
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
                List.of("APP_DB_PANEL_RUNTIME", "MAX_BOT_TOKEN", "SERVER_PORT"),
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

    @Test
    void runtimeContractReturnsStructuredErrorWhenRuntimeServiceThrows() throws Exception {
        Channel channel = new Channel();
        channel.setId(53L);
        channel.setPlatform("telegram");

        when(channelRepository.findById(53L)).thenReturn(Optional.of(channel));
        when(botProcessService.describeRuntimeContract(channel))
            .thenThrow(new IllegalStateException("Не найден собранный jar для модуля bot-telegram"));

        mockMvc.perform(get("/api/bots/53/runtime-contract"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Не найден собранный jar для модуля bot-telegram"));
    }

    @Test
    void runtimeContractReturnsFallbackErrorWhenExceptionMessageIsBlank() throws Exception {
        Channel channel = new Channel();
        channel.setId(57L);
        channel.setPlatform("telegram");

        when(channelRepository.findById(57L)).thenReturn(Optional.of(channel));
        when(botProcessService.describeRuntimeContract(channel))
            .thenThrow(new IllegalStateException("   "));

        mockMvc.perform(get("/api/bots/57/runtime-contract"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Не удалось построить runtime contract"));
    }

    @Test
    void runtimeContractReturnsFallbackErrorWhenExceptionMessageIsNull() throws Exception {
        Channel channel = new Channel();
        channel.setId(58L);
        channel.setPlatform("telegram");

        when(channelRepository.findById(58L)).thenReturn(Optional.of(channel));
        when(botProcessService.describeRuntimeContract(channel))
            .thenThrow(new IllegalStateException((String) null));

        mockMvc.perform(get("/api/bots/58/runtime-contract"))
            .andExpect(status().isInternalServerError())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error").value("Не удалось построить runtime contract"));
    }

    @Test
    void stopTreatsStoppedCaseInsensitivelyAsSuccess() throws Exception {
        when(botProcessService.stop(59L)).thenReturn(
            new BotProcessService.BotProcessStatus(false, "STOPPED", null)
        );

        mockMvc.perform(post("/api/bots/59/stop"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.status").value("STOPPED"));
    }

    @Test
    void startReturnsUnknownStatusWhenServiceMessageIsBlank() throws Exception {
        Channel channel = new Channel();
        channel.setId(54L);
        channel.setPlatform("telegram");

        when(channelRepository.findById(54L)).thenReturn(Optional.of(channel));
        when(botProcessService.start(channel)).thenReturn(
            new BotProcessService.BotProcessStatus(false, "   ", null)
        );

        mockMvc.perform(post("/api/bots/54/start"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.status").value("unknown"));
    }

    @Test
    void startTreatsStoppedCaseInsensitivelyAsSuccess() throws Exception {
        Channel channel = new Channel();
        channel.setId(61L);
        channel.setPlatform("telegram");

        when(channelRepository.findById(61L)).thenReturn(Optional.of(channel));
        when(botProcessService.start(channel)).thenReturn(
            new BotProcessService.BotProcessStatus(false, "STOPPED", null)
        );

        mockMvc.perform(post("/api/bots/61/start"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.status").value("STOPPED"));
    }

    @Test
    void stopReturnsUnknownStatusWhenServiceMessageIsBlank() throws Exception {
        when(botProcessService.stop(55L)).thenReturn(
            new BotProcessService.BotProcessStatus(false, "", null)
        );

        mockMvc.perform(post("/api/bots/55/stop"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.status").value("unknown"));
    }

    @Test
    void statusReturnsUnknownStatusWhenServiceMessageIsBlank() throws Exception {
        when(botProcessService.status(56L)).thenReturn(
            new BotProcessService.BotProcessStatus(false, "   ", null)
        );

        mockMvc.perform(get("/api/bots/56/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.status").value("unknown"));
    }

    @Test
    void statusReturnsUnknownStatusWhenServiceMessageIsNull() throws Exception {
        when(botProcessService.status(62L)).thenReturn(
            new BotProcessService.BotProcessStatus(false, null, null)
        );

        mockMvc.perform(get("/api/bots/62/status"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.status").value("unknown"));
    }

    @Test
    void webRoleForwardsStartToBotRunnerAndReturnsAcknowledgement() throws Exception {
        Channel channel = new Channel();
        channel.setId(71L);
        when(runtimeRoleProperties.getRole()).thenReturn("panel-web");
        when(channelRepository.findById(71L)).thenReturn(Optional.of(channel));
        when(botRunnerLifecycleClient.start(71L)).thenReturn(new BotLifecycleCommandResult(
            "cmd-71", 71L, "start", true, "running", "2026-09-19T15:00:00Z", "runner-1"
        ));

        mockMvc.perform(post("/api/bots/71/start"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.status").value("running"))
            .andExpect(jsonPath("$.commandId").value("cmd-71"))
            .andExpect(jsonPath("$.runnerInstanceId").value("runner-1"));

        verify(botRunnerLifecycleClient).start(71L);
        verify(botProcessService, never()).start(channel);
    }

    @Test
    void webRoleForwardsStopToBotRunner() throws Exception {
        when(runtimeRoleProperties.getRole()).thenReturn("panel-web");
        when(botRunnerLifecycleClient.stop(72L)).thenReturn(new BotLifecycleCommandResult(
            "cmd-72", 72L, "stop", true, "stopped", null, "runner-1"
        ));

        mockMvc.perform(post("/api/bots/72/stop"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("stopped"))
            .andExpect(jsonPath("$.commandId").value("cmd-72"));

        verify(botRunnerLifecycleClient).stop(72L);
        verify(botProcessService, never()).stop(72L);
    }

    @Test
    void workerRoleStillRejectsManualLifecycle() throws Exception {
        Channel channel = new Channel();
        channel.setId(73L);
        when(runtimeRoleProperties.getRole()).thenReturn("ops-worker");
        when(channelRepository.findById(73L)).thenReturn(Optional.of(channel));

        mockMvc.perform(post("/api/bots/73/start"))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false));

        verify(botRunnerLifecycleClient, never()).start(73L);
        verify(botProcessService, never()).start(channel);
    }
}
