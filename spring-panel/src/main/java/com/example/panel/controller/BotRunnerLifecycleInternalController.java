package com.example.panel.controller;

import com.example.panel.entity.Channel;
import com.example.panel.repository.ChannelRepository;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeRoleProperties;
import com.example.panel.security.InternalBotApiRequestGuardService;
import com.example.panel.service.BotLifecycleCommandResult;
import com.example.panel.service.BotProcessService;
import com.example.panel.service.BotProcessService.BotProcessStatus;
import jakarta.servlet.http.HttpServletRequest;
import java.util.function.Supplier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/api/bot/runtime")
public class BotRunnerLifecycleInternalController {

    private final BotProcessService botProcessService;
    private final ChannelRepository channelRepository;
    private final RuntimeRoleProperties runtimeRoleProperties;
    private final InternalBotApiRequestGuardService requestGuardService;

    public BotRunnerLifecycleInternalController(BotProcessService botProcessService,
                                                ChannelRepository channelRepository,
                                                RuntimeRoleProperties runtimeRoleProperties,
                                                InternalBotApiRequestGuardService requestGuardService) {
        this.botProcessService = botProcessService;
        this.channelRepository = channelRepository;
        this.runtimeRoleProperties = runtimeRoleProperties;
        this.requestGuardService = requestGuardService;
    }

    @PostMapping("/{channelId}/start")
    public ResponseEntity<String> start(
        HttpServletRequest request,
        @RequestHeader(name = InternalBotApiRequestGuardService.AUTH_HEADER, required = false) String token,
        @RequestHeader(name = InternalBotApiRequestGuardService.IDEMPOTENCY_HEADER, required = false) String commandId,
        @PathVariable Long channelId
    ) {
        return executeWrite(request, token, () -> {
            requireRunnerRole();
            String confirmedCommandId = requireCommandId(commandId);
            Channel channel = channelRepository.findById(channelId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found"));
            BotProcessStatus status = botProcessService.start(channel);
            return result(confirmedCommandId, channelId, "start", status);
        });
    }

    @PostMapping("/{channelId}/stop")
    public ResponseEntity<String> stop(
        HttpServletRequest request,
        @RequestHeader(name = InternalBotApiRequestGuardService.AUTH_HEADER, required = false) String token,
        @RequestHeader(name = InternalBotApiRequestGuardService.IDEMPOTENCY_HEADER, required = false) String commandId,
        @PathVariable Long channelId
    ) {
        return executeWrite(request, token, () -> {
            requireRunnerRole();
            String confirmedCommandId = requireCommandId(commandId);
            BotProcessStatus status = botProcessService.stop(channelId);
            return result(confirmedCommandId, channelId, "stop", status);
        });
    }

    private ResponseEntity<String> executeWrite(HttpServletRequest request,
                                                String token,
                                                Supplier<Object> action) {
        InternalBotApiRequestGuardService.WriteExecution execution = requestGuardService.prepareWrite(request, token);
        if (execution.replayResponse() != null) {
            return execution.replayResponse();
        }
        try {
            return requestGuardService.successResponse(execution, action.get());
        } catch (RuntimeException ex) {
            execution.release();
            throw ex;
        }
    }

    private void requireRunnerRole() {
        RuntimeRole role = runtimeRoleProperties.resolvedRole();
        if (role != RuntimeRole.BOT_RUNNER && role != RuntimeRole.ALL) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Bot lifecycle command reached a non-runner role");
        }
    }

    private String requireCommandId(String commandId) {
        if (!StringUtils.hasText(commandId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bot lifecycle command id is required");
        }
        return commandId.trim();
    }

    private BotLifecycleCommandResult result(String commandId,
                                             Long channelId,
                                             String action,
                                             BotProcessStatus status) {
        String message = status == null || !StringUtils.hasText(status.message()) ? "unknown" : status.message().trim();
        boolean success = status != null && (status.running() || "stopped".equalsIgnoreCase(message));
        return new BotLifecycleCommandResult(
            commandId,
            channelId,
            action,
            success,
            message,
            status != null && status.startedAt() != null ? status.startedAt().toString() : null,
            runtimeRoleProperties.resolvedInstanceId()
        );
    }
}
