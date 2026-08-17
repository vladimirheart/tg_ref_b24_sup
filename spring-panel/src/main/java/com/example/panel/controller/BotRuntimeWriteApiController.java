package com.example.panel.controller;

import com.example.panel.entity.Channel;
import com.example.panel.service.BotRuntimeChannelService;
import com.example.panel.service.BotRuntimeTicketWriteService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/internal/api/bot")
public class BotRuntimeWriteApiController {

    private static final String AUTH_HEADER = "X-Iguana-Bot-Api-Token";

    private final BotRuntimeTicketWriteService ticketWriteService;
    private final BotRuntimeChannelService channelService;
    private final String expectedToken;

    public BotRuntimeWriteApiController(BotRuntimeTicketWriteService ticketWriteService,
                                        BotRuntimeChannelService channelService,
                                        @Value("${app.bots.internal-api.token:iguana-internal-bot-token}") String expectedToken) {
        this.ticketWriteService = ticketWriteService;
        this.channelService = channelService;
        this.expectedToken = expectedToken;
    }

    @PutMapping("/tickets/{ticketId}/activity")
    public BotRuntimeTicketWriteService.MutationResult registerActivity(
        @RequestHeader(name = AUTH_HEADER, required = false) String token,
        @PathVariable String ticketId,
        @RequestBody(required = false) TicketActivityRequest request
    ) {
        requireAuthorized(token);
        return ticketWriteService.registerActivity(ticketId, request != null ? request.userIdentity() : null);
    }

    @DeleteMapping("/tickets/{ticketId}/activity")
    public BotRuntimeTicketWriteService.MutationResult clearActivity(
        @RequestHeader(name = AUTH_HEADER, required = false) String token,
        @PathVariable String ticketId
    ) {
        requireAuthorized(token);
        return ticketWriteService.clearActivity(ticketId);
    }

    @PostMapping("/tickets/{ticketId}/reopen")
    public BotRuntimeTicketWriteService.MutationResult reopenTicket(
        @RequestHeader(name = AUTH_HEADER, required = false) String token,
        @PathVariable String ticketId
    ) {
        requireAuthorized(token);
        return ticketWriteService.reopenTicket(ticketId);
    }

    @PostMapping("/tickets/{ticketId}/operator-relay")
    public BotRuntimeTicketWriteService.MutationResult recordOperatorRelay(
        @RequestHeader(name = AUTH_HEADER, required = false) String token,
        @PathVariable String ticketId,
        @RequestBody OperatorRelayRequest request
    ) {
        requireAuthorized(token);
        return ticketWriteService.recordOperatorRelay(
            ticketId,
            request != null ? request.message() : null,
            request != null ? request.telegramMessageId() : null,
            request != null ? request.replyToTelegramId() : null,
            request != null ? request.operatorIdentity() : null
        );
    }

    @PutMapping("/channels/{channelId}/messages/{telegramMessageId}/client-edit")
    public BotRuntimeTicketWriteService.MutationResult markClientMessageEdited(
        @RequestHeader(name = AUTH_HEADER, required = false) String token,
        @PathVariable Long channelId,
        @PathVariable Long telegramMessageId,
        @RequestBody ClientMessageEditRequest request
    ) {
        requireAuthorized(token);
        return ticketWriteService.markClientMessageEdited(
            channelId,
            telegramMessageId,
            request != null ? request.message() : null
        );
    }

    @PostMapping("/channels/resolve")
    public ChannelResponse resolveChannel(
        @RequestHeader(name = AUTH_HEADER, required = false) String token,
        @RequestBody(required = false) ChannelResolveRequest request
    ) {
        requireAuthorized(token);
        Channel resolved = channelService.resolveConfiguredChannel(
            request != null ? request.channelId() : null,
            request != null ? request.token() : null,
            request != null ? request.channelName() : null,
            request != null ? request.platform() : null
        );
        return ChannelResponse.from(resolved);
    }

    @PutMapping("/channels/{channelId}/support-chat")
    public ChannelResponse updateSupportChatId(
        @RequestHeader(name = AUTH_HEADER, required = false) String token,
        @PathVariable Long channelId,
        @RequestBody(required = false) SupportChatUpdateRequest request
    ) {
        requireAuthorized(token);
        Channel updated = channelService.updateSupportChatId(
            channelId,
            request != null ? request.supportChatId() : null
        );
        return ChannelResponse.from(updated);
    }

    @PostMapping("/feedback/pending/{requestId}/submit")
    public BotRuntimeTicketWriteService.MutationResult storeFeedback(
        @RequestHeader(name = AUTH_HEADER, required = false) String token,
        @PathVariable Long requestId,
        @RequestBody FeedbackSubmitRequest request
    ) {
        requireAuthorized(token);
        return ticketWriteService.storeFeedback(
            requestId,
            request != null ? request.rating() : null
        );
    }

    private void requireAuthorized(String token) {
        if (token == null || token.isBlank() || !token.equals(expectedToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized internal bot API request");
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

    public record FeedbackSubmitRequest(Integer rating) {
    }

    public record ChannelResolveRequest(Long channelId,
                                        String token,
                                        String channelName,
                                        String platform) {
    }

    public record SupportChatUpdateRequest(String supportChatId) {
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
