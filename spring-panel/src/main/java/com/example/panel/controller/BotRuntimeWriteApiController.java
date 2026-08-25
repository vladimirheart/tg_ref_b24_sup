package com.example.panel.controller;

import com.example.panel.entity.Channel;
import com.example.panel.security.InternalBotApiRequestGuardService;
import com.example.panel.service.BotRuntimeBlacklistService;
import com.example.panel.service.BotRuntimeChannelService;
import com.example.panel.service.BotRuntimeTicketWriteService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.function.Supplier;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.http.ResponseEntity;

@RestController
@RequestMapping("/internal/api/bot")
public class BotRuntimeWriteApiController {

    private static final String AUTH_HEADER = "X-Iguana-Bot-Api-Token";

    private final BotRuntimeTicketWriteService ticketWriteService;
    private final BotRuntimeChannelService channelService;
    private final BotRuntimeBlacklistService blacklistService;
    private final InternalBotApiRequestGuardService requestGuardService;

    public BotRuntimeWriteApiController(BotRuntimeTicketWriteService ticketWriteService,
                                        BotRuntimeChannelService channelService,
                                        BotRuntimeBlacklistService blacklistService,
                                        InternalBotApiRequestGuardService requestGuardService) {
        this.ticketWriteService = ticketWriteService;
        this.channelService = channelService;
        this.blacklistService = blacklistService;
        this.requestGuardService = requestGuardService;
    }

    @PutMapping("/tickets/{ticketId}/activity")
    public ResponseEntity<String> registerActivity(
        HttpServletRequest request,
        @RequestHeader(name = AUTH_HEADER, required = false) String token,
        @PathVariable String ticketId,
        @RequestBody(required = false) TicketActivityRequest body
    ) {
        return executeWrite(request, token, () ->
            ticketWriteService.registerActivity(ticketId, body != null ? body.userIdentity() : null));
    }

    @DeleteMapping("/tickets/{ticketId}/activity")
    public ResponseEntity<String> clearActivity(
        HttpServletRequest request,
        @RequestHeader(name = AUTH_HEADER, required = false) String token,
        @PathVariable String ticketId
    ) {
        return executeWrite(request, token, () -> ticketWriteService.clearActivity(ticketId));
    }

    @PostMapping("/tickets/{ticketId}/reopen")
    public ResponseEntity<String> reopenTicket(
        HttpServletRequest request,
        @RequestHeader(name = AUTH_HEADER, required = false) String token,
        @PathVariable String ticketId,
        @RequestBody(required = false) TicketActivityRequest body
    ) {
        return executeWrite(request, token, () ->
            ticketWriteService.reopenTicket(ticketId, body != null ? body.userIdentity() : null));
    }

    @PostMapping("/tickets/{ticketId}/operator-relay")
    public ResponseEntity<String> recordOperatorRelay(
        HttpServletRequest request,
        @RequestHeader(name = AUTH_HEADER, required = false) String token,
        @PathVariable String ticketId,
        @RequestBody OperatorRelayRequest body
    ) {
        return executeWrite(request, token, () -> ticketWriteService.recordOperatorRelay(
            ticketId,
            body != null ? body.message() : null,
            body != null ? body.telegramMessageId() : null,
            body != null ? body.replyToTelegramId() : null,
            body != null ? body.operatorIdentity() : null
        ));
    }

    @PutMapping("/channels/{channelId}/messages/{telegramMessageId}/client-edit")
    public ResponseEntity<String> markClientMessageEdited(
        HttpServletRequest request,
        @RequestHeader(name = AUTH_HEADER, required = false) String token,
        @PathVariable Long channelId,
        @PathVariable Long telegramMessageId,
        @RequestBody ClientMessageEditRequest body
    ) {
        return executeWrite(request, token, () -> ticketWriteService.markClientMessageEdited(
            channelId,
            telegramMessageId,
            body != null ? body.message() : null
        ));
    }

    @PutMapping("/tickets/{ticketId}/operator-messages/{telegramMessageId}")
    public ResponseEntity<String> markOperatorMessageEdited(
        HttpServletRequest request,
        @RequestHeader(name = AUTH_HEADER, required = false) String token,
        @PathVariable String ticketId,
        @PathVariable Long telegramMessageId,
        @RequestBody OperatorMessageEditRequest body
    ) {
        return executeWrite(request, token, () -> ticketWriteService.markOperatorMessageEdited(
            ticketId,
            telegramMessageId,
            body != null ? body.message() : null,
            body != null ? body.operatorIdentity() : null
        ));
    }

    @PostMapping("/channels/resolve")
    public ResponseEntity<String> resolveChannel(
        HttpServletRequest request,
        @RequestHeader(name = AUTH_HEADER, required = false) String token,
        @RequestBody(required = false) ChannelResolveRequest body
    ) {
        return executeWrite(request, token, () -> {
            Channel resolved = channelService.resolveConfiguredChannel(
                body != null ? body.channelId() : null,
                body != null ? body.token() : null,
                body != null ? body.channelName() : null,
                body != null ? body.platform() : null
            );
            return ChannelResponse.from(resolved);
        });
    }

    @PutMapping("/channels/{channelId}/support-chat")
    public ResponseEntity<String> updateSupportChatId(
        HttpServletRequest request,
        @RequestHeader(name = AUTH_HEADER, required = false) String token,
        @PathVariable Long channelId,
        @RequestBody(required = false) SupportChatUpdateRequest body
    ) {
        return executeWrite(request, token, () -> {
            Channel updated = channelService.updateSupportChatId(
                channelId,
                body != null ? body.supportChatId() : null
            );
            return ChannelResponse.from(updated);
        });
    }

    @PostMapping("/feedback/pending/{requestId}/submit")
    public ResponseEntity<String> storeFeedback(
        HttpServletRequest request,
        @RequestHeader(name = AUTH_HEADER, required = false) String token,
        @PathVariable Long requestId,
        @RequestBody FeedbackSubmitRequest body
    ) {
        return executeWrite(request, token, () -> ticketWriteService.storeFeedback(
            requestId,
            body != null ? body.rating() : null
        ));
    }

    @PostMapping("/blacklist/unblock-requests")
    public ResponseEntity<String> requestUnblock(
        HttpServletRequest request,
        @RequestHeader(name = AUTH_HEADER, required = false) String token,
        @RequestBody(required = false) UnblockRequestCreateRequest body
    ) {
        return executeWrite(request, token, () -> {
            Duration cooldown = body != null && body.cooldownSeconds() != null
                ? Duration.ofSeconds(Math.max(0L, body.cooldownSeconds()))
                : Duration.ZERO;
            return blacklistService.requestUnblock(
                body != null ? body.userId() : null,
                body != null ? body.reason() : null,
                body != null ? body.channelId() : null,
                cooldown
            );
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

    public record TicketActivityRequest(String userIdentity) {
    }

    public record OperatorRelayRequest(String message,
                                       Long telegramMessageId,
                                       Long replyToTelegramId,
                                       String operatorIdentity) {
    }

    public record ClientMessageEditRequest(String message) {
    }

    public record OperatorMessageEditRequest(String message,
                                             String operatorIdentity) {
    }

    public record FeedbackSubmitRequest(Integer rating) {
    }

    public record ChannelResolveRequest(Long channelId,
                                        String token,
                                        String channelName,
                                        String platform) {
    }

    public record SupportChatUpdateRequest(String supportChatId) {
    }

    public record UnblockRequestCreateRequest(Long userId,
                                              String reason,
                                              Long channelId,
                                              Long cooldownSeconds) {
    }

    public record ChannelResponse(Long id,
                                  String token,
                                  String channelName,
                                  String questionsCfg,
                                  Integer maxQuestions,
                                  Boolean active,
                                  String botUsername,
                                  String questionTemplateId,
                                  String ratingTemplateId,
                                  String publicId,
                                  String autoActionTemplateId,
                                  String description,
                                  String filters,
                                  String deliverySettings,
                                  String platform,
                                  String platformConfig,
                                  Long credentialId,
                                  String supportChatId) {
        static ChannelResponse from(Channel channel) {
            return new ChannelResponse(
                channel.getId(),
                channel.getToken(),
                channel.getChannelName(),
                channel.getQuestionsCfg(),
                channel.getMaxQuestions(),
                channel.getActive(),
                channel.getBotUsername(),
                channel.getQuestionTemplateId(),
                channel.getRatingTemplateId(),
                channel.getPublicId(),
                channel.getAutoActionTemplateId(),
                channel.getDescription(),
                channel.getFilters(),
                channel.getDeliverySettings(),
                channel.getPlatform(),
                channel.getPlatformConfig(),
                channel.getCredentialId(),
                channel.getSupportChatId()
            );
        }
    }
}
