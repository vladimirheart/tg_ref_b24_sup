package com.example.panel.service;

import com.example.panel.runtime.RuntimeWorkload;
import com.example.panel.runtime.RuntimeRole;
import com.example.panel.runtime.RuntimeReplicaPolicy;
import com.example.panel.config.PanelIntegrationTransportMode;
import com.example.panel.entity.Channel;
import com.example.panel.entity.ChatHistory;
import com.example.panel.entity.Ticket;
import com.example.panel.entity.TicketActive;
import com.example.panel.entity.TicketSpan;
import com.example.panel.repository.ChatHistoryRepository;
import com.example.panel.repository.TicketActiveRepository;
import com.example.panel.repository.TicketRepository;
import com.example.panel.repository.TicketSpanRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@RuntimeWorkload(
    id = "dialog-auto-close-scheduler-service",
    roles = {RuntimeRole.WORKER},
    replicaPolicy = RuntimeReplicaPolicy.LEASED
)@Component
public class DialogAutoCloseSchedulerService {

    private static final Logger log = LoggerFactory.getLogger(DialogAutoCloseSchedulerService.class);
    private static final Duration DEFAULT_AUTO_CLOSE_DURATION = Duration.ofHours(24);
    private static final String AUTO_CLOSE_RESOLVED_BY = "auto_close";
    private static final String AUTO_CLOSE_SOURCE = "inactivity";
    private static final String AUTO_CLOSE_TEXT = "Диалог автоматически закрыт из-за отсутствия активности.";
    private static final List<String> ACTIVITY_SENDERS = Arrays.asList("client", "operator", "support", "admin", "ai_agent");

    private final TicketActiveRepository ticketActiveRepository;
    private final TicketRepository ticketRepository;
    private final TicketSpanRepository ticketSpanRepository;
    private final ChatHistoryRepository chatHistoryRepository;
    private final SharedConfigService sharedConfigService;
    private final UiEventOutboxAppendService uiEventOutboxAppendService;
    private final DialogAutoCloseFollowUpTaskService dialogAutoCloseFollowUpTaskService;
    private final PanelIntegrationTransportMode integrationTransportMode;
    private final JdbcTemplate jdbcTemplate;
    private final RuntimeCoordinationService runtimeCoordinationService;

    public DialogAutoCloseSchedulerService(TicketActiveRepository ticketActiveRepository,
                                           TicketRepository ticketRepository,
                                           TicketSpanRepository ticketSpanRepository,
                                           ChatHistoryRepository chatHistoryRepository,
                                           SharedConfigService sharedConfigService,
                                           UiEventOutboxAppendService uiEventOutboxAppendService,
                                           DialogAutoCloseFollowUpTaskService dialogAutoCloseFollowUpTaskService,
                                           PanelIntegrationTransportMode integrationTransportMode,
                                           JdbcTemplate jdbcTemplate,
                                           RuntimeCoordinationService runtimeCoordinationService) {
        this.ticketActiveRepository = ticketActiveRepository;
        this.ticketRepository = ticketRepository;
        this.ticketSpanRepository = ticketSpanRepository;
        this.chatHistoryRepository = chatHistoryRepository;
        this.sharedConfigService = sharedConfigService;
        this.uiEventOutboxAppendService = uiEventOutboxAppendService;
        this.dialogAutoCloseFollowUpTaskService = dialogAutoCloseFollowUpTaskService;
        this.integrationTransportMode = integrationTransportMode;
        this.jdbcTemplate = jdbcTemplate;
        this.runtimeCoordinationService = runtimeCoordinationService;
    }

    @Scheduled(cron = "0 */10 * * * *")
    @Transactional
    public void autoCloseInactiveTickets() {
        if (!integrationTransportMode.isRabbitMqMode()) {
            return;
        }
        runtimeCoordinationService.runWithLease("dialog-auto-close", Duration.ofMinutes(5), () -> {
            AutoCloseRunResult result = runAutoCloseSweep(sharedConfigService.loadSettings());
            log.info("Panel auto-close scheduler checked {} active tickets and closed {}",
                result.checkedTickets(), result.closedTickets());
        });
    }

    @Transactional
    AutoCloseRunResult runAutoCloseSweep(Map<String, Object> settings) {
        List<TicketActive> activeTickets = ticketActiveRepository.findAll();
        OffsetDateTime now = OffsetDateTime.now();
        int checked = 0;
        int closed = 0;

        for (TicketActive active : activeTickets) {
            checked++;
            if (active == null || !StringUtils.hasText(active.getTicketId())) {
                continue;
            }
            Optional<Ticket> ticketOpt = ticketRepository.findByIdTicketId(active.getTicketId());
            if (ticketOpt.isEmpty()) {
                ticketActiveRepository.deleteById(active.getTicketId());
                continue;
            }
            Ticket ticket = ticketOpt.get();
            AutoCloseSelection selection = resolveAutoCloseSelection(settings, ticket.getChannel());
            if (!selection.enabled() || selection.duration() == null
                || selection.duration().isZero() || selection.duration().isNegative()) {
                continue;
            }
            if (isResolvedStatus(ticket.getStatus())) {
                ticketActiveRepository.deleteById(active.getTicketId());
                continue;
            }
            OffsetDateTime lastActivity = resolveLastActivityAt(active);
            if (lastActivity == null || !lastActivity.isBefore(now.minus(selection.duration()))) {
                continue;
            }
            if (closeTicket(ticket, active, now)) {
                closed++;
            }
        }
        return new AutoCloseRunResult(checked, closed);
    }

    private boolean closeTicket(Ticket ticket, TicketActive active, OffsetDateTime now) {
        ticket.setStatus("closed");
        ticket.setResolvedAt(now);
        ticket.setResolvedBy(AUTO_CLOSE_RESOLVED_BY);
        ticket.setClosedCount(Optional.ofNullable(ticket.getClosedCount()).orElse(0) + 1);
        long totalWork = Optional.ofNullable(ticket.getWorkTimeTotalSec()).orElse(0L);
        long spanSeconds = closeOpenSpan(ticket.getTicketId(), now);
        ticket.setWorkTimeTotalSec(totalWork + spanSeconds);
        ticketRepository.save(ticket);
        ticketActiveRepository.deleteById(active.getTicketId());

        ChatHistory event = new ChatHistory();
        event.setUserId(ticket.getUserId());
        event.setSender("system");
        event.setMessage(AUTO_CLOSE_TEXT);
        event.setTimestamp(now);
        event.setTicketId(ticket.getTicketId());
        event.setMessageType("system_event");
        event.setChannel(ticket.getChannel());
        chatHistoryRepository.save(event);

        uiEventOutboxAppendService.publishTicketClosed(ticket.getTicketId(),
            ticket.getChannel() != null ? ticket.getChannel().getId() : null,
            AUTO_CLOSE_TEXT,
            true);
        ensurePendingFeedbackRequest(ticket.getTicketId(), AUTO_CLOSE_RESOLVED_BY, now);
        dialogAutoCloseFollowUpTaskService.createTaskForAutoClosedDialog(ticket.getTicketId());
        return true;
    }

    private long closeOpenSpan(String ticketId, OffsetDateTime now) {
        return ticketSpanRepository.findFirstByTicketIdAndEndedAtIsNullOrderBySpanNoDesc(ticketId)
            .map(span -> {
                span.setEndedAt(now);
                long seconds = Duration.between(span.getStartedAt(), now).getSeconds();
                span.setDurationSeconds(seconds);
                ticketSpanRepository.save(span);
                return seconds;
            })
            .orElse(0L);
    }

    private void ensurePendingFeedbackRequest(String ticketId, String resolvedBy, OffsetDateTime now) {
        String source = isAutoCloseResolvedBy(resolvedBy) ? "auto_close" : "operator_close";
        OffsetDateTime createdAt = now;
        OffsetDateTime expiresAt = now.plusDays(1);
        int updated = jdbcTemplate.update("""
                UPDATE pending_feedback_requests
                   SET expires_at = ?, source = ?
                 WHERE ticket_id = ?
                """,
            expiresAt,
            source,
            ticketId
        );
        if (updated > 0) {
            return;
        }
        Ticket ticket = ticketRepository.findByIdTicketId(ticketId).orElse(null);
        if (ticket == null || ticket.getUserId() == null || ticket.getChannel() == null || ticket.getChannel().getId() == null) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO pending_feedback_requests
                    (user_id, channel_id, ticket_id, source, created_at, expires_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """,
            ticket.getUserId(),
            ticket.getChannel().getId(),
            ticketId,
            source,
            createdAt,
            expiresAt
        );
    }

    private OffsetDateTime resolveLastActivityAt(TicketActive active) {
        OffsetDateTime latest = active != null ? active.getLastSeen() : null;
        String ticketId = active != null ? active.getTicketId() : null;
        if (!StringUtils.hasText(ticketId)) {
            return latest;
        }
        OffsetDateTime historyActivity = chatHistoryRepository
            .findTopByTicketIdAndSenderInOrderByIdDesc(ticketId, ACTIVITY_SENDERS)
            .map(ChatHistory::getTimestamp)
            .orElse(null);
        if (historyActivity != null && (latest == null || historyActivity.isAfter(latest))) {
            return historyActivity;
        }
        return latest;
    }

    private boolean isResolvedStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT);
        return "resolved".equals(normalized) || "closed".equals(normalized);
    }

    private boolean isAutoCloseResolvedBy(String resolvedBy) {
        if (!StringUtils.hasText(resolvedBy)) {
            return false;
        }
        String normalized = resolvedBy.trim().toLowerCase(Locale.ROOT);
        return "авто-система".equals(normalized) || "auto_close".equals(normalized);
    }

    private AutoCloseSelection resolveAutoCloseSelection(Map<String, Object> settings, Channel channel) {
        AutoCloseSelection channelSelection = resolveChannelAutoCloseSelection(settings, channel);
        if (channelSelection != null) {
            return channelSelection;
        }
        AutoCloseSelection templateSelection = resolveGlobalTemplateSelection(settings);
        if (templateSelection != null) {
            return templateSelection;
        }
        return AutoCloseSelection.enabled(DEFAULT_AUTO_CLOSE_DURATION, "default:auto_close", null,
            Math.toIntExact(DEFAULT_AUTO_CLOSE_DURATION.toHours()));
    }

    private AutoCloseSelection resolveChannelAutoCloseSelection(Map<String, Object> settings, Channel channel) {
        if (channel == null) {
            return null;
        }
        String templateId = trimToNull(channel.getAutoActionTemplateId());
        if (templateId == null) {
            return null;
        }
        Map<String, Object> template = findAutoCloseTemplate(settings, templateId);
        return buildTemplateSelection(template, "channel:auto_action_template_id");
    }

    private AutoCloseSelection resolveGlobalTemplateSelection(Map<String, Object> settings) {
        Map<String, Object> autoCloseConfig = asMap(settings.get("auto_close_config"));
        List<Map<String, Object>> templates = asMapList(autoCloseConfig.get("templates"));
        if (templates.isEmpty()) {
            return null;
        }
        String activeTemplateId = trimToNull(autoCloseConfig.get("active_template_id"));
        Map<String, Object> selected = null;
        if (activeTemplateId != null) {
            selected = templates.stream()
                .filter(template -> Objects.equals(activeTemplateId, trimToNull(template.get("id"))))
                .findFirst()
                .orElse(null);
        }
        if (selected == null) {
            selected = templates.get(0);
        }
        return buildTemplateSelection(selected, "auto_close_config.active_template");
    }

    private Map<String, Object> findAutoCloseTemplate(Map<String, Object> settings, String templateId) {
        Map<String, Object> autoCloseConfig = asMap(settings.get("auto_close_config"));
        List<Map<String, Object>> templates = asMapList(autoCloseConfig.get("templates"));
        for (Map<String, Object> template : templates) {
            if (Objects.equals(templateId, trimToNull(template.get("id")))) {
                return template;
            }
        }
        return null;
    }

    private AutoCloseSelection buildTemplateSelection(Map<String, Object> template, String source) {
        if (template == null || template.isEmpty()) {
            return null;
        }
        int hours = parsePositiveInteger(template.get("hours"));
        String templateId = trimToNull(template.get("id"));
        if (hours < 0) {
            return null;
        }
        if (hours == 0) {
            return AutoCloseSelection.disabled(source, templateId, 0);
        }
        return AutoCloseSelection.enabled(Duration.ofHours(hours), source, templateId, hours);
    }

    private Map<String, Object> asMap(Object rawValue) {
        if (!(rawValue instanceof Map<?, ?> map)) {
            return Map.of();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            if (key != null) {
                result.put(key.toString(), value);
            }
        });
        return result;
    }

    private List<Map<String, Object>> asMapList(Object rawValue) {
        if (!(rawValue instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> entry = new LinkedHashMap<>();
            map.forEach((key, value) -> {
                if (key != null) {
                    entry.put(key.toString(), value);
                }
            });
            result.add(entry);
        }
        return result;
    }

    private String trimToNull(Object rawValue) {
        if (rawValue == null) {
            return null;
        }
        String normalized = rawValue.toString().trim();
        return normalized.isEmpty() ? null : normalized;
    }

    private int parsePositiveInteger(Object rawValue) {
        if (rawValue == null) {
            return -1;
        }
        if (rawValue instanceof Number number) {
            return number.intValue();
        }
        String normalized = String.valueOf(rawValue).trim();
        if (normalized.isEmpty()) {
            return -1;
        }
        try {
            return Integer.parseInt(normalized);
        } catch (NumberFormatException ex) {
            return -1;
        }
    }

    record AutoCloseSelection(Duration duration,
                              boolean enabled,
                              String source,
                              String templateId,
                              Integer hours) {
        static AutoCloseSelection enabled(Duration duration, String source, String templateId, Integer hours) {
            return new AutoCloseSelection(duration, true, source, templateId, hours);
        }

        static AutoCloseSelection disabled(String source, String templateId, Integer hours) {
            return new AutoCloseSelection(null, false, source, templateId, hours);
        }
    }

    record AutoCloseRunResult(int checkedTickets, int closedTickets) {
    }
}
