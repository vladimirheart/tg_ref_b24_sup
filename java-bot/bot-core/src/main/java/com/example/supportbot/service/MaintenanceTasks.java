package com.example.supportbot.service;

import com.example.supportbot.entity.Channel;
import com.example.supportbot.entity.ClientUnblockRequest;
import com.example.supportbot.repository.ClientUnblockRequestRepository;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class MaintenanceTasks {

    private static final Logger log = LoggerFactory.getLogger(MaintenanceTasks.class);
    private static final Duration DEFAULT_AUTO_CLOSE_DURATION = Duration.ofHours(24);

    private final ClientUnblockRequestRepository unblockRequestRepository;
    private final TicketService ticketService;
    private final SharedConfigService sharedConfigService;

    public MaintenanceTasks(ClientUnblockRequestRepository unblockRequestRepository,
                           TicketService ticketService,
                           SharedConfigService sharedConfigService) {
        this.unblockRequestRepository = unblockRequestRepository;
        this.ticketService = ticketService;
        this.sharedConfigService = sharedConfigService;
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void expireOldUnblockRequests() {
        OffsetDateTime threshold = OffsetDateTime.now().minusDays(30);
        List<ClientUnblockRequest> requests = unblockRequestRepository.findAll();
        int updated = 0;
        for (ClientUnblockRequest request : requests) {
            if (!"pending".equalsIgnoreCase(request.getStatus())) {
                continue;
            }
            OffsetDateTime createdAt = request.getCreatedAt();
            if (createdAt != null && createdAt.isBefore(threshold)) {
                request.setStatus("expired");
                request.setDecidedAt(OffsetDateTime.now());
                request.setDecisionComment("Auto-expired by scheduler");
                unblockRequestRepository.save(request);
                updated++;
            }
        }
        if (updated > 0) {
            log.info("Marked {} unblock requests as expired", updated);
        }
    }

    @Scheduled(cron = "0 */10 * * * *")
    @Transactional
    public void autoCloseInactiveTickets() {
        Map<String, Object> settings = sharedConfigService.loadSettings();
        AutoCloseSelection defaultSelection = resolveAutoCloseSelection(settings, null);
        if (defaultSelection.enabled() && defaultSelection.duration() != null) {
            log.info("Auto-close default config selected: source={}, templateId={}, hours={}, threshold={}",
                    defaultSelection.source(),
                    defaultSelection.templateId(),
                    defaultSelection.hours(),
                    OffsetDateTime.now().minus(defaultSelection.duration()));
        } else {
            log.info("Auto-close default config is disabled: source={}, templateId={}, hours={}",
                    defaultSelection.source(),
                    defaultSelection.templateId(),
                    defaultSelection.hours());
        }

        TicketService.AutoCloseRunResult result = ticketService.closeInactiveTickets(ticket -> {
            AutoCloseSelection selection = resolveAutoCloseSelection(settings, ticket != null ? ticket.getChannel() : null);
            OffsetDateTime threshold = selection.enabled() && selection.duration() != null
                    ? OffsetDateTime.now().minus(selection.duration())
                    : null;
            log.debug("Resolved auto-close config for ticket {} (channelId={}): source={}, templateId={}, hours={}, enabled={}, threshold={}",
                    ticket != null ? ticket.getTicketId() : null,
                    ticket != null && ticket.getChannel() != null ? ticket.getChannel().getId() : null,
                    selection.source(),
                    selection.templateId(),
                    selection.hours(),
                    selection.enabled(),
                    threshold);
            return selection.enabled()
                    ? TicketService.AutoClosePolicy.enabled(
                            selection.duration(),
                            selection.source(),
                            selection.templateId(),
                            selection.hours())
                    : TicketService.AutoClosePolicy.disabled(
                            selection.source(),
                            selection.templateId(),
                            selection.hours());
        });

        log.info("Auto-close scheduler checked {} active tickets and closed {}",
                result.checkedTickets(), result.closedTickets());
    }

    Duration resolveAutoCloseDuration() {
        AutoCloseSelection selection = resolveAutoCloseSelection(sharedConfigService.loadSettings(), null);
        if (!selection.enabled()) {
            return null;
        }
        return selection.duration();
    }

    private AutoCloseSelection resolveAutoCloseSelection(Map<String, Object> settings,
                                                         Channel channel) {
        AutoCloseSelection channelSelection = resolveChannelAutoCloseSelection(settings, channel);
        if (channelSelection != null) {
            return channelSelection;
        }

        AutoCloseSelection templateSelection = resolveGlobalTemplateSelection(settings);
        if (templateSelection != null) {
            return templateSelection;
        }

        if (hasAutoCloseConfig(settings)) {
            if (parsePositiveInteger(settings.get("auto_close_hours")) >= 0) {
                log.warn("Ignoring deprecated legacy auto_close_hours because auto_close_config is present but did not resolve a valid template selection");
            }
            return AutoCloseSelection.enabled(DEFAULT_AUTO_CLOSE_DURATION, "default:auto_close", null,
                    Math.toIntExact(DEFAULT_AUTO_CLOSE_DURATION.toHours()));
        }

        AutoCloseSelection migrationSelection = resolveMigrationOnlyAutoCloseSelection(settings);
        if (migrationSelection != null) {
            return migrationSelection;
        }

        return AutoCloseSelection.enabled(DEFAULT_AUTO_CLOSE_DURATION, "default:auto_close", null,
                Math.toIntExact(DEFAULT_AUTO_CLOSE_DURATION.toHours()));
    }

    private AutoCloseSelection resolveMigrationOnlyAutoCloseSelection(Map<String, Object> settings) {
        int legacyHours = parsePositiveInteger(settings.get("auto_close_hours"));
        if (legacyHours == 0) {
            log.warn("Using migration-only fallback from deprecated top-level auto_close_hours to disable auto-close because auto_close_config is absent");
            return AutoCloseSelection.disabled("migration:auto_close_hours", null, 0);
        }
        if (legacyHours > 0) {
            log.warn("Using migration-only fallback from deprecated top-level auto_close_hours because auto_close_config is absent");
            return AutoCloseSelection.enabled(Duration.ofHours(legacyHours), "migration:auto_close_hours", null, legacyHours);
        }
        return null;
    }

    private AutoCloseSelection resolveChannelAutoCloseSelection(Map<String, Object> settings,
                                                                Channel channel) {
        if (channel == null) {
            return null;
        }
        String templateId = trimToNull(channel.getAutoActionTemplateId());
        if (templateId == null) {
            return null;
        }
        Map<String, Object> template = findAutoCloseTemplate(settings, templateId);
        if (template == null) {
            log.debug("Channel {} requested auto-close template {} but it was not found. Falling back to global config.",
                    channel.getId(), templateId);
            return null;
        }
        AutoCloseSelection selection = buildTemplateSelection(template, "channel:auto_action_template_id");
        if (selection == null) {
            log.debug("Channel {} requested auto-close template {} but it has no valid hours. Falling back to global config.",
                    channel.getId(), templateId);
        }
        return selection;
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

    private Map<String, Object> findAutoCloseTemplate(Map<String, Object> settings,
                                                      String templateId) {
        Map<String, Object> autoCloseConfig = asMap(settings.get("auto_close_config"));
        List<Map<String, Object>> templates = asMapList(autoCloseConfig.get("templates"));
        for (Map<String, Object> template : templates) {
            if (Objects.equals(templateId, trimToNull(template.get("id")))) {
                return template;
            }
        }
        return null;
    }

    private AutoCloseSelection buildTemplateSelection(Map<String, Object> template,
                                                      String source) {
        if (template == null || template.isEmpty()) {
            return null;
        }
        int hours = extractTemplateHours(template);
        String templateId = trimToNull(template.get("id"));
        if (hours < 0) {
            return null;
        }
        if (hours == 0) {
            return AutoCloseSelection.disabled(source, templateId, 0);
        }
        return AutoCloseSelection.enabled(Duration.ofHours(hours), source, templateId, hours);
    }

    private int extractTemplateHours(Map<String, Object> template) {
        int hours = parsePositiveInteger(template.get("hours"));
        if (hours >= 0) {
            return hours;
        }
        hours = parsePositiveInteger(template.get("timeout_hours"));
        if (hours >= 0) {
            log.warn("Using deprecated auto-close template.timeout_hours at bot runtime for template {}. Canonical key is hours.",
                    trimToNull(template.get("id")));
            return hours;
        }
        hours = parsePositiveInteger(template.get("auto_close_hours"));
        if (hours >= 0) {
            log.warn("Using deprecated auto-close template.auto_close_hours at bot runtime for template {}. Canonical key is hours.",
                    trimToNull(template.get("id")));
        }
        return hours;
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

    private boolean hasAutoCloseConfig(Map<String, Object> settings) {
        return settings != null && settings.containsKey("auto_close_config");
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

    private record AutoCloseSelection(Duration duration,
                                      boolean enabled,
                                      String source,
                                      String templateId,
                                      Integer hours) {
        private static AutoCloseSelection enabled(Duration duration,
                                                  String source,
                                                  String templateId,
                                                  Integer hours) {
            return new AutoCloseSelection(duration, true, source, templateId, hours);
        }

        private static AutoCloseSelection disabled(String source,
                                                   String templateId,
                                                   Integer hours) {
            return new AutoCloseSelection(null, false, source, templateId, hours);
        }
    }
}
