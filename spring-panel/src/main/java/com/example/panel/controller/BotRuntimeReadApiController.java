package com.example.panel.controller;

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
    private final String expectedToken;

    public BotRuntimeReadApiController(BotRuntimeTicketReadService ticketReadService,
                                       @Value("${app.bots.internal-api.token:iguana-internal-bot-token}") String expectedToken) {
        this.ticketReadService = ticketReadService;
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

    private void requireAuthorized(String token) {
        if (token == null || token.isBlank() || !token.equals(expectedToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthorized internal bot API request");
        }
    }
}
