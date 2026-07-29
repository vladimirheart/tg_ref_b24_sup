package com.example.panel.service;

import com.example.panel.entity.Channel;
import com.example.panel.model.dialog.DialogListItem;
import com.example.panel.model.dialog.DialogOperatorOption;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChannelAssignmentRoutingService {

    private final ObjectMapper objectMapper;
    private final SharedConfigService sharedConfigService;
    private final DialogParticipantService dialogParticipantService;
    private final DialogLookupReadService dialogLookupReadService;
    private final Map<String, Integer> roundRobinCursorByRoute = new ConcurrentHashMap<>();

    public ChannelAssignmentRoutingService(ObjectMapper objectMapper,
                                           SharedConfigService sharedConfigService,
                                           DialogParticipantService dialogParticipantService,
                                           DialogLookupReadService dialogLookupReadService) {
        this.objectMapper = objectMapper;
        this.sharedConfigService = sharedConfigService;
        this.dialogParticipantService = dialogParticipantService;
        this.dialogLookupReadService = dialogLookupReadService;
    }

    public ResolvedAssignmentRouting resolve(Channel channel, RoutingEvent event, String ticketId) {
        AssignmentRoutingConfig config = parseConfig(channel);
        if (!config.enabled() || config.mode() == RoutingMode.DISABLED) {
            return ResolvedAssignmentRouting.disabled();
        }

        Map<String, DialogOperatorOption> directory = loadOperatorDirectory();
        List<String> routingCandidates = resolveRoutingCandidates(config, directory);
        if (routingCandidates.isEmpty()) {
            return new ResolvedAssignmentRouting(true, List.of(), null, config.mode().wireValue(), config.strategy().wireValue());
        }

        String assignee = null;
        if (event == RoutingEvent.NEW_PUBLIC_APPEAL && config.assignResponsibleOnCreate()) {
            assignee = resolveAssignee(config, routingCandidates, ticketId);
        }

        return new ResolvedAssignmentRouting(
                true,
                List.copyOf(new LinkedHashSet<>(routingCandidates)),
                assignee,
                config.mode().wireValue(),
                config.strategy().wireValue()
        );
    }

    public Map<String, Object> loadCatalogPayload() {
        List<DialogOperatorOption> operators = dialogParticipantService.loadAssignableOperators();
        LinkedHashSet<String> departments = new LinkedHashSet<>();
        operators.stream()
                .map(DialogOperatorOption::department)
                .filter(StringUtils::hasText)
                .map(String::trim)
                .forEach(departments::add);
        JsonNode orgStructure = sharedConfigService.loadOrgStructure();
        if (orgStructure != null && orgStructure.path("nodes").isArray()) {
            for (JsonNode node : orgStructure.path("nodes")) {
                String name = trimToNull(node.path("name").asText(""));
                if (name != null) {
                    departments.add(name);
                }
            }
        }
        List<String> sortedDepartments = departments.stream()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();

        List<Map<String, Object>> operatorPayload = operators.stream()
                .map(operator -> Map.<String, Object>of(
                        "username", safe(operator.username()),
                        "display_name", safe(operator.displayName()),
                        "department", safe(operator.department()),
                        "role", safe(operator.role()),
                        "avatar_url", safe(operator.avatarUrl())
                ))
                .toList();

        return Map.of(
                "operators", operatorPayload,
                "departments", sortedDepartments,
                "orgStructure", orgStructure != null
                        ? objectMapper.convertValue(orgStructure, Map.class)
                        : Map.of("nodes", List.of())
        );
    }

    private Map<String, DialogOperatorOption> loadOperatorDirectory() {
        Map<String, DialogOperatorOption> directory = new LinkedHashMap<>();
        for (DialogOperatorOption option : dialogParticipantService.loadAssignableOperators()) {
            String normalized = normalizeIdentity(option.username());
            if (normalized != null) {
                directory.putIfAbsent(normalized, option);
            }
        }
        return directory;
    }

    private List<String> resolveRoutingCandidates(AssignmentRoutingConfig config,
                                                  Map<String, DialogOperatorOption> directory) {
        return switch (config.mode()) {
            case SINGLE_OPERATOR -> resolveSingleOperatorCandidate(config, directory);
            case OPERATOR_POOL -> resolvePoolCandidates(config, directory);
            case DEPARTMENT_QUEUE -> resolveDepartmentCandidates(config, directory);
            case DISABLED -> List.of();
        };
    }

    private List<String> resolveSingleOperatorCandidate(AssignmentRoutingConfig config,
                                                        Map<String, DialogOperatorOption> directory) {
        String operator = normalizeIdentity(config.operatorUsername());
        if (operator == null || !directory.containsKey(operator)) {
            return List.of();
        }
        return List.of(operator);
    }

    private List<String> resolvePoolCandidates(AssignmentRoutingConfig config,
                                               Map<String, DialogOperatorOption> directory) {
        LinkedHashSet<String> usernames = new LinkedHashSet<>();
        for (String username : config.operatorUsernames()) {
            String normalized = normalizeIdentity(username);
            if (normalized != null && directory.containsKey(normalized)) {
                usernames.add(normalized);
            }
        }
        return new ArrayList<>(usernames);
    }

    private List<String> resolveDepartmentCandidates(AssignmentRoutingConfig config,
                                                     Map<String, DialogOperatorOption> directory) {
        String department = trimToNull(config.department());
        if (department == null) {
            return List.of();
        }
        return directory.values().stream()
                .filter(option -> sameDepartment(option.department(), department))
                .map(DialogOperatorOption::username)
                .map(this::normalizeIdentity)
                .filter(StringUtils::hasText)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private String resolveAssignee(AssignmentRoutingConfig config,
                                   List<String> routingCandidates,
                                   String ticketId) {
        if (routingCandidates.isEmpty()) {
            return null;
        }
        if (config.mode() == RoutingMode.SINGLE_OPERATOR) {
            return routingCandidates.get(0);
        }
        return switch (config.strategy()) {
            case HASH_BY_TICKET -> resolvePoolAssigneeByHash(routingCandidates, ticketId);
            case LEAST_LOADED -> resolvePoolAssigneeLeastLoaded(routingCandidates);
            case ROUND_ROBIN -> resolvePoolAssigneeRoundRobin(config, routingCandidates);
        };
    }

    private String resolvePoolAssigneeByHash(List<String> assigneePool, String ticketId) {
        int idx = Math.floorMod(String.valueOf(ticketId).hashCode(), assigneePool.size());
        return assigneePool.get(idx);
    }

    private String resolvePoolAssigneeRoundRobin(AssignmentRoutingConfig config, List<String> assigneePool) {
        String routeKey = config.mode().wireValue() + "::" + normalizeIdentity(config.operatorUsername()) + "::"
                + trimToNull(config.department()) + "::" + String.join(",", assigneePool);
        int cursor = roundRobinCursorByRoute.compute(routeKey, (key, value) -> value == null ? 0 : value + 1);
        return assigneePool.get(Math.floorMod(cursor, assigneePool.size()));
    }

    private String resolvePoolAssigneeLeastLoaded(List<String> assigneePool) {
        return assigneePool.stream()
                .sorted(Comparator.comparingLong(this::loadOpenCount)
                        .thenComparing(String::compareToIgnoreCase))
                .findFirst()
                .orElse(null);
    }

    private long loadOpenCount(String operator) {
        if (!StringUtils.hasText(operator) || dialogLookupReadService == null) {
            return Long.MAX_VALUE;
        }
        return dialogLookupReadService.loadDialogs(operator).stream()
                .filter(dialog -> isOpenDialog(dialog))
                .count();
    }

    private boolean isOpenDialog(DialogListItem dialog) {
        if (dialog == null || !StringUtils.hasText(dialog.statusKey())) {
            return true;
        }
        String normalized = dialog.statusKey().trim().toLowerCase(Locale.ROOT);
        return !normalized.contains("closed") && !normalized.contains("resolved");
    }

    private AssignmentRoutingConfig parseConfig(Channel channel) {
        if (channel == null || !StringUtils.hasText(channel.getQuestionsCfg())) {
            return AssignmentRoutingConfig.disabled();
        }
        try {
            JsonNode root = objectMapper.readTree(channel.getQuestionsCfg());
            JsonNode node = root.path("assignmentRouting");
            if (!node.isObject()) {
                return AssignmentRoutingConfig.disabled();
            }
            boolean enabled = node.path("enabled").asBoolean(false);
            RoutingMode mode = RoutingMode.from(node.path("mode").asText(""));
            boolean assignResponsibleOnCreate = node.path("assignResponsibleOnCreate").asBoolean(false);
            AssignmentStrategy strategy = AssignmentStrategy.from(node.path("strategy").asText(""));
            String operatorUsername = trimToNull(node.path("operatorUsername").asText(""));
            List<String> operatorUsernames = readStringList(node.path("operatorUsernames"));
            String department = trimToNull(node.path("department").asText(""));
            return new AssignmentRoutingConfig(enabled, mode, assignResponsibleOnCreate, strategy, operatorUsername, operatorUsernames, department);
        } catch (Exception ignored) {
            return AssignmentRoutingConfig.disabled();
        }
    }

    private List<String> readStringList(JsonNode node) {
        if (!node.isArray()) {
            return List.of();
        }
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (JsonNode item : node) {
            String normalized = normalizeIdentity(item.asText(""));
            if (normalized != null) {
                values.add(normalized);
            }
        }
        return new ArrayList<>(values);
    }

    private boolean sameDepartment(String left, String right) {
        return StringUtils.hasText(left)
                && StringUtils.hasText(right)
                && left.trim().equalsIgnoreCase(right.trim());
    }

    private String normalizeIdentity(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? null : trimmed.toLowerCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public enum RoutingEvent {
        NEW_PUBLIC_APPEAL,
        INCOMING_CLIENT_MESSAGE,
        FIRST_RESPONSE_OVERDUE
    }

    public record ResolvedAssignmentRouting(boolean enabled,
                                            List<String> recipients,
                                            String assignee,
                                            String mode,
                                            String strategy) {

        public static ResolvedAssignmentRouting disabled() {
            return new ResolvedAssignmentRouting(false, List.of(), null, RoutingMode.DISABLED.wireValue(), AssignmentStrategy.ROUND_ROBIN.wireValue());
        }
    }

    private record AssignmentRoutingConfig(boolean enabled,
                                           RoutingMode mode,
                                           boolean assignResponsibleOnCreate,
                                           AssignmentStrategy strategy,
                                           String operatorUsername,
                                           List<String> operatorUsernames,
                                           String department) {

        private static AssignmentRoutingConfig disabled() {
            return new AssignmentRoutingConfig(false, RoutingMode.DISABLED, false, AssignmentStrategy.ROUND_ROBIN, null, List.of(), null);
        }
    }

    private enum RoutingMode {
        DISABLED("disabled"),
        SINGLE_OPERATOR("single_operator"),
        OPERATOR_POOL("operator_pool"),
        DEPARTMENT_QUEUE("department_queue");

        private final String wireValue;

        RoutingMode(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }

        static RoutingMode from(String raw) {
            String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "single_operator" -> SINGLE_OPERATOR;
                case "operator_pool" -> OPERATOR_POOL;
                case "department_queue" -> DEPARTMENT_QUEUE;
                default -> DISABLED;
            };
        }
    }

    private enum AssignmentStrategy {
        ROUND_ROBIN("round_robin"),
        LEAST_LOADED("least_loaded"),
        HASH_BY_TICKET("hash_by_ticket");

        private final String wireValue;

        AssignmentStrategy(String wireValue) {
            this.wireValue = wireValue;
        }

        public String wireValue() {
            return wireValue;
        }

        static AssignmentStrategy from(String raw) {
            String normalized = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
            return switch (normalized) {
                case "least_loaded", "least_load", "load" -> LEAST_LOADED;
                case "hash_by_ticket", "hash", "ticket_hash" -> HASH_BY_TICKET;
                default -> ROUND_ROBIN;
            };
        }
    }
}
