package com.example.panel.service;

import com.example.panel.entity.Message;
import com.example.panel.entity.TicketResponsible;
import com.example.panel.repository.MessageRepository;
import com.example.panel.repository.TicketResponsibleRepository;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionOperations;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
public class DialogAutoCloseFollowUpTaskService {

    private static final Logger log = LoggerFactory.getLogger(DialogAutoCloseFollowUpTaskService.class);
    private static final String TASK_CREATOR = "auto_close";
    private static final String TASK_SOURCE = "dialog_auto_close";
    private static final String TASK_TAG = "dialogs";
    private static final int TITLE_PROBLEM_LIMIT = 72;

    private final PanelTaskService panelTaskService;
    private final TicketResponsibleRepository ticketResponsibleRepository;
    private final MessageRepository messageRepository;
    private final JdbcTemplate jdbcTemplate;
    private final TransactionOperations isolatedTransactionOperations;

    public DialogAutoCloseFollowUpTaskService(PanelTaskService panelTaskService,
                                              TicketResponsibleRepository ticketResponsibleRepository,
                                              MessageRepository messageRepository,
                                              JdbcTemplate jdbcTemplate,
                                              PlatformTransactionManager transactionManager) {
        this.panelTaskService = panelTaskService;
        this.ticketResponsibleRepository = ticketResponsibleRepository;
        this.messageRepository = messageRepository;
        this.jdbcTemplate = jdbcTemplate;
        this.isolatedTransactionOperations = buildRequiresNewTransactionOperations(transactionManager);
    }

    public void createTaskForAutoClosedDialog(String ticketId) {
        String normalizedTicketId = trimToNull(ticketId);
        if (normalizedTicketId == null) {
            return;
        }
        try {
            isolatedTransactionOperations.execute(status -> {
                createTaskTransactional(normalizedTicketId);
                return null;
            });
        } catch (Exception ex) {
            log.warn("Unable to create follow-up task for auto-closed ticket {}: {}", normalizedTicketId,
                ex.getMessage(), ex);
        }
    }

    private void createTaskTransactional(String ticketId) {
        String responsible = resolveResponsible(ticketId);
        if (responsible == null) {
            log.debug("Skipping auto-close follow-up task for ticket {} because no responsible is assigned", ticketId);
            return;
        }

        Message message = messageRepository.findByTicketId(ticketId).orElse(null);
        List<String> coExecutors = loadCoExecutors(ticketId, responsible);

        panelTaskService.createTask(new PanelTaskService.TaskPayload(
            buildTitle(ticketId, message),
            buildBodyHtml(ticketId, message, responsible, coExecutors),
            TASK_CREATOR,
            responsible,
            TASK_TAG,
            null,
            null,
            TASK_SOURCE,
            coExecutors,
            List.of(),
            List.of(ticketId)
        ));
    }

    private static TransactionOperations buildRequiresNewTransactionOperations(PlatformTransactionManager transactionManager) {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private String resolveResponsible(String ticketId) {
        return ticketResponsibleRepository.findById(ticketId)
            .map(TicketResponsible::getResponsible)
            .map(this::trimToNull)
            .orElse(null);
    }

    private List<String> loadCoExecutors(String ticketId, String responsible) {
        try {
            List<String> usernames = jdbcTemplate.query("""
                    SELECT username
                      FROM ticket_participants
                     WHERE ticket_id = ?
                     ORDER BY CASE WHEN added_at IS NULL THEN 0 ELSE 1 END ASC,
                              added_at ASC,
                              lower(username) ASC
                    """,
                (rs, rowNum) -> rs.getString("username"),
                ticketId
            );
            if (usernames == null || usernames.isEmpty()) {
                return List.of();
            }
            String normalizedResponsible = normalizeIdentity(responsible);
            Set<String> seen = new LinkedHashSet<>();
            List<String> coExecutors = new ArrayList<>();
            for (String username : usernames) {
                String trimmed = trimToNull(username);
                String normalized = normalizeIdentity(trimmed);
                if (trimmed == null || normalized == null || normalized.equals(normalizedResponsible) || !seen.add(normalized)) {
                    continue;
                }
                coExecutors.add(trimmed);
            }
            return coExecutors;
        } catch (DataAccessException ex) {
            log.warn("Unable to load dialog participants for auto-closed ticket {}: {}", ticketId, ex.getMessage());
            return List.of();
        }
    }

    private String buildTitle(String ticketId, Message message) {
        String problem = message != null ? trimToNull(message.getProblem()) : null;
        if (problem == null) {
            return "Проверить автозакрытый диалог #" + ticketId;
        }
        return "Проверить автозакрытый диалог #" + ticketId + ": " + abbreviate(problem, TITLE_PROBLEM_LIMIT);
    }

    private String buildBodyHtml(String ticketId,
                                 Message message,
                                 String responsible,
                                 List<String> coExecutors) {
        List<String> details = new ArrayList<>();
        appendDetail(details, "Ответственный", responsible);
        appendDetail(details, "Клиент", firstNonBlank(message != null ? message.getClientName() : null,
            message != null ? message.getUsername() : null));
        appendDetail(details, "Проблема", message != null ? message.getProblem() : null);
        appendDetail(details, "Бизнес", message != null ? message.getBusiness() : null);
        appendDetail(details, "Локация", joinLocation(message));
        if (coExecutors != null && !coExecutors.isEmpty()) {
            appendDetail(details, "Соисполнители", coExecutors.stream()
                .map(this::trimToNull)
                .filter(StringUtils::hasText)
                .collect(Collectors.joining(", ")));
        }

        StringBuilder html = new StringBuilder();
        html.append("<p>Диалог был автоматически закрыт из-за отсутствия активности. Нужно проверить обращение и при необходимости связаться с клиентом.</p>");
        if (!details.isEmpty()) {
            html.append("<ul>");
            details.forEach(detail -> html.append("<li>").append(detail).append("</li>"));
            html.append("</ul>");
        }
        html.append("<p><a href=\"/dialogs/")
            .append(escapeHtml(ticketId))
            .append("\">Открыть диалог #")
            .append(escapeHtml(ticketId))
            .append("</a></p>");
        return html.toString();
    }

    private void appendDetail(List<String> details, String label, String value) {
        String normalizedValue = trimToNull(value);
        if (details == null || label == null || normalizedValue == null) {
            return;
        }
        details.add("<strong>" + escapeHtml(label) + ":</strong> " + escapeHtml(normalizedValue));
    }

    private String joinLocation(Message message) {
        if (message == null) {
            return null;
        }
        List<String> parts = new ArrayList<>();
        addIfPresent(parts, message.getLocationType());
        addIfPresent(parts, message.getCity());
        addIfPresent(parts, message.getLocationName());
        return parts.isEmpty() ? null : String.join(", ", parts);
    }

    private void addIfPresent(List<String> target, String value) {
        String normalized = trimToNull(value);
        if (target != null && normalized != null) {
            target.add(normalized);
        }
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String normalized = trimToNull(value);
            if (normalized != null) {
                return normalized;
            }
        }
        return null;
    }

    private String abbreviate(String value, int maxLength) {
        String normalized = trimToNull(value);
        if (normalized == null || normalized.length() <= maxLength) {
            return normalized;
        }
        return normalized.substring(0, Math.max(0, maxLength - 1)).trim() + "...";
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        StringBuilder escaped = new StringBuilder(value.length());
        for (char ch : value.toCharArray()) {
            switch (ch) {
                case '&' -> escaped.append("&amp;");
                case '<' -> escaped.append("&lt;");
                case '>' -> escaped.append("&gt;");
                case '"' -> escaped.append("&quot;");
                case '\'' -> escaped.append("&#39;");
                default -> escaped.append(ch);
            }
        }
        return escaped.toString();
    }

    private String normalizeIdentity(String value) {
        String trimmed = trimToNull(value);
        return trimmed != null ? trimmed.toLowerCase(Locale.ROOT) : null;
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
