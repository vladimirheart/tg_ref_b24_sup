package com.example.panel.controller;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeRoleProperties;
import com.example.panel.service.BotLifecycleCommandResult;
import com.example.panel.service.BotProcessService;
import com.example.panel.service.BotProcessService.BotProcessStatus;
import com.example.panel.service.BotRunnerLifecycleClient;
import com.example.panel.service.UiEventStreamService;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/bots")
public class BotProcessApiController {

    private final BotProcessService botProcessService;
    private final BotRunnerLifecycleClient botRunnerLifecycleClient;
    private final ChannelRepository channelRepository;
    private final UiEventStreamService uiEventStreamService;
    private final RuntimeRoleProperties runtimeRoleProperties;

    public BotProcessApiController(BotProcessService botProcessService,
                                   BotRunnerLifecycleClient botRunnerLifecycleClient,
                                   ChannelRepository channelRepository,
                                   UiEventStreamService uiEventStreamService,
                                   RuntimeRoleProperties runtimeRoleProperties) {
        this.botProcessService = botProcessService;
        this.botRunnerLifecycleClient = botRunnerLifecycleClient;
        this.channelRepository = channelRepository;
        this.uiEventStreamService = uiEventStreamService;
        this.runtimeRoleProperties = runtimeRoleProperties;
    }

    @PostMapping("/{channelId}/start")
    public ResponseEntity<Map<String, Object>> start(@PathVariable Long channelId) {
        Channel channel = channelRepository.findById(channelId).orElse(null);
        if (channel == null) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", false);
            response.put("error", "Канал не найден");
            return ResponseEntity.status(404).body(response);
        }

        RuntimeRole role = RuntimeRole.from(runtimeRoleProperties.getRole());
        if (role == RuntimeRole.WEB) {
            return forwardLifecycleCommand(channelId, "bot_started", () -> botRunnerLifecycleClient.start(channelId));
        }
        ResponseEntity<Map<String, Object>> denied = rejectUnsupportedLifecycleRole(role);
        if (denied != null) {
            return denied;
        }

        BotProcessStatus status = botProcessService.start(channel);
        uiEventStreamService.publishSidebarBotsChanged("bot_started", channelId);
        return ResponseEntity.ok(buildStatusResponse(
            isSuccessfulStatus(status),
            statusMessage(status),
            status == null ? null : status.startedAt()
        ));
    }

    @PostMapping("/{channelId}/stop")
    public ResponseEntity<Map<String, Object>> stop(@PathVariable Long channelId) {
        RuntimeRole role = RuntimeRole.from(runtimeRoleProperties.getRole());
        if (role == RuntimeRole.WEB) {
            return forwardLifecycleCommand(channelId, "bot_stopped", () -> botRunnerLifecycleClient.stop(channelId));
        }
        ResponseEntity<Map<String, Object>> denied = rejectUnsupportedLifecycleRole(role);
        if (denied != null) {
            return denied;
        }

        BotProcessStatus status = botProcessService.stop(channelId);
        uiEventStreamService.publishSidebarBotsChanged("bot_stopped", channelId);
        return ResponseEntity.ok(buildStatusResponse(isSuccessfulStatus(status), statusMessage(status), null));
    }

    @GetMapping("/{channelId}/status")
    public ResponseEntity<Map<String, Object>> status(@PathVariable Long channelId) {
        BotProcessStatus status = botProcessService.status(channelId);
        return ResponseEntity.ok(buildStatusResponse(
            isSuccessfulStatus(status),
            statusMessage(status),
            status == null ? null : status.startedAt()
        ));
    }

    @GetMapping("/{channelId}/runtime-contract")
    public ResponseEntity<Map<String, Object>> runtimeContract(@PathVariable Long channelId) {
        Channel channel = channelRepository.findById(channelId).orElse(null);
        if (channel == null) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", false);
            response.put("error", "Канал не найден");
            return ResponseEntity.status(404).body(response);
        }
        try {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", true);
            response.put("contract", botProcessService.describeRuntimeContract(channel));
            return ResponseEntity.ok(response);
        } catch (Exception ex) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", false);
            response.put("error", ex.getMessage() == null || ex.getMessage().isBlank()
                ? "Не удалось построить runtime contract"
                : ex.getMessage());
            return ResponseEntity.internalServerError().body(response);
        }
    }

    private ResponseEntity<Map<String, Object>> forwardLifecycleCommand(Long channelId,
                                                                        String uiEventReason,
                                                                        LifecycleCommand action) {
        try {
            BotLifecycleCommandResult result = action.execute();
            uiEventStreamService.publishSidebarBotsChanged(uiEventReason, channelId);
            Map<String, Object> response = buildStatusResponse(result.success(), result.status(), result.startedAt());
            response.put("commandId", result.commandId());
            response.put("runnerInstanceId", result.runnerInstanceId());
            return ResponseEntity.ok(response);
        } catch (IllegalStateException ex) {
            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", false);
            response.put("status", "Не удалось подтвердить команду bot-runner: " + ex.getMessage());
            return ResponseEntity.status(503).body(response);
        }
    }

    private Map<String, Object> buildStatusResponse(boolean success, String status, Object startedAt) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", success);
        response.put("status", status);
        response.put("startedAt", startedAt);
        return response;
    }

    private boolean isSuccessfulStatus(BotProcessStatus status) {
        if (status == null) {
            return false;
        }
        return status.running() || "stopped".equalsIgnoreCase(status.message());
    }

    private String statusMessage(BotProcessStatus status) {
        if (status == null || status.message() == null || status.message().isBlank()) {
            return "unknown";
        }
        return status.message();
    }

    private ResponseEntity<Map<String, Object>> rejectUnsupportedLifecycleRole(RuntimeRole role) {
        if (role == RuntimeRole.ALL || role == RuntimeRole.BOT_RUNNER) {
            return null;
        }
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("success", false);
        response.put("status", "Управление ботами выполняет bot-runner; команда недоступна из роли " + role.externalName() + ".");
        return ResponseEntity.status(409).body(response);
    }

    @FunctionalInterface
    private interface LifecycleCommand {
        BotLifecycleCommandResult execute();
    }
}
