package com.example.panel.controller;

import com.example.panel.entity.Channel;
import com.example.panel.service.BotRuntimeBlacklistService;
import com.example.panel.service.BotRuntimeChannelService;
import com.example.panel.service.BotRuntimeConfigService;
import com.example.panel.service.BotRuntimeTicketReadService;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/internal/api/bot")
public class BotRuntimeReadApiController {

    private static final String AUTH_HEADER = "X-Iguana-Bot-Api-Token";

    private final BotRuntimeTicketReadService ticketReadService;
    private final BotRuntimeChannelService channelService;
    private final BotRuntimeConfigService runtimeConfigService;
    private final BotRuntimeBlacklistService blacklistService;
    private final String expectedToken;

    public BotRuntimeReadApiController(BotRuntimeTicketReadService ticketReadService,
                                       BotRuntimeChannelService channelService,
                                       BotRuntimeConfigService runtimeConfigService,
                                       BotRuntimeBlacklistService blacklistService,
                                       @Value("${app.bots.internal-api.token:iguana-internal-bot-token}") String expectedToken) {
        this.ticketReadService = ticketReadService;
        this.channelService = channelService;
        this.runtimeConfigService = runtimeConfigService;
        this.blacklistService = blacklistService;
        this.expectedToken = expectedToken;
    }

    @GetMapping("/tickets/active")
    public BotRuntimeTicketReadService.ActiveTicketLookup activeTicket(
        @RequestHeader(name = AUTH_HEADER, required = false) String token,
        @RequestParam(required = false) Long userId,
        @RequestParam(required = false) String username,
        @RequestParam(required = false) Long channelId
    ) {
        requireAuthorized(token);
        return ticketReadService.findActiveTicket(userId, username, channelId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Active ticket not found"));
    }

    @GetMapping("/tickets/{ticketId}")
    public BotRuntimeTicketReadService.TicketLookup ticket(
        @RequestHeader(name = AUTH_HEADER, required = false) String token,
        @PathVariable String ticketId
    ) {
        requireAuthorized(token);
        return ticketReadService.findTicket(ticketId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket not found"));
    }

    @GetMapping("/tickets/{ticketId}/request-number")
    public BotRuntimeTicketReadService.RequestNumberLookup requestNumber(
        @RequestHeader(name = AUTH_HEADER, required = false) String token,
        @PathVariable String ticketId
    ) {
        requireAuthorized(token);
        return ticketReadService.resolveRequestNumber(ticketId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Request number not found"));
    }

    @GetMapping("/users/{userId}/last-ticket-context")
    public BotRuntimeTicketReadService.LastTicketContext lastTicketContext(
        @RequestHeader(name = AUTH_HEADER, required = false) String token,
        @PathVariable Long userId
    ) {
        requireAuthorized(token);
        return ticketReadService.findLastTicketContext(userId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Last ticket context not found"));
    }

    @GetMapping("/users/{userId}/tickets/recent")
    public List<BotRuntimeTicketReadService.TicketSummaryLookup> recentTickets(
        @RequestHeader(name = AUTH_HEADER, required = false) String token,
        @PathVariable Long userId,
        @RequestParam(defaultValue = "10") int limit
    ) {
        requireAuthorized(token);
        return ticketReadService.findRecentTickets(userId, limit);
    }

    @GetMapping("/users/{userId}/feedback/pending")
    public BotRuntimeTicketReadService.PendingFeedbackRequestLookup pendingFeedbackRequest(
        @RequestHeader(name = AUTH_HEADER, required = false) String token,
        @PathVariable Long userId,
        @RequestParam(required = false) Long channelId
    ) {
        requireAuthorized(token);
        return ticketReadService.findActiveFeedbackRequest(userId, channelId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Pending feedback request not found"));
    }

    @GetMapping("/channels/{channelId}")
    public ChannelLookup channel(
        @RequestHeader(name = AUTH_HEADER, required = false) String token,
        @PathVariable Long channelId
    ) {
        requireAuthorized(token);
        return channelService.findChannel(channelId)
            .map(ChannelLookup::from)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Channel not found"));
    }

    @GetMapping("/channels/{channelId}/runtime-config")
    public BotRuntimeConfigService.RuntimeConfigLookup runtimeConfig(
        @RequestHeader(name = AUTH_HEADER, required = false) String token,
        @PathVariable Long channelId
    ) {
        requireAuthorized(token);
        return runtimeConfigService.findRuntimeConfig(channelId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Runtime config not found"));
    }

    @GetMapping("/blacklist/status")
    public BotRuntimeBlacklistService.ResolvedBlacklistStatusLookup blacklistStatus(
        @RequestHeader(name = AUTH_HEADER, required = false) String token,
        @RequestParam(required = false) Long userId,
        @RequestParam(name = "alias", required = false) List<String> aliases
    ) {
        requireAuthorized(token);
        return blacklistService.resolveStatus(userId, aliases);
    }

    @GetMapping("/unblock-requests/pending-summary")
    public BotRuntimeBlacklistService.PendingUnblockSummaryLookup pendingUnblockSummary(
        @RequestHeader(name = AUTH_HEADER, required = false) String token,
        @RequestParam(defaultValue = "3") int limit
    ) {
        requireAuthorized(token);
        return blacklistService.pendingSummary(limit);
    }

    private void requireAuthorized(String token) {
        if (token == null || token.isBlank() || !token.equals(expectedToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized internal bot API request");
        }
    }

    public record ChannelLookup(Long id,
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
        static ChannelLookup from(Channel channel) {
            return new ChannelLookup(
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
