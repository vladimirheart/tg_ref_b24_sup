package com.example.panel.service;

import com.example.panel.entity.AutomationRun;
import com.example.panel.entity.AutomationRunItem;
import com.example.panel.repository.AutomationRunItemRepository;
import com.example.panel.repository.AutomationRunRepository;
import com.example.panel.service.EmployeeDiscountAutomationSettingsService.EmployeeDiscountAutomationSettings;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EmployeeDiscountAutomationService {

    public static final String AUTOMATION_KEY = "employee_discount_automation";

    private final EmployeeDiscountAutomationSettingsService settingsService;
    private final EmployeeDiscountAutomationCredentialService credentialService;
    private final Bitrix24RestService bitrix24RestService;
    private final IikoDirectoryService iikoDirectoryService;
    private final AutomationRunRepository automationRunRepository;
    private final AutomationRunItemRepository automationRunItemRepository;

    public EmployeeDiscountAutomationService(EmployeeDiscountAutomationSettingsService settingsService,
                                             EmployeeDiscountAutomationCredentialService credentialService,
                                             Bitrix24RestService bitrix24RestService,
                                             IikoDirectoryService iikoDirectoryService,
                                             AutomationRunRepository automationRunRepository,
                                             AutomationRunItemRepository automationRunItemRepository) {
        this.settingsService = settingsService;
        this.credentialService = credentialService;
        this.bitrix24RestService = bitrix24RestService;
        this.iikoDirectoryService = iikoDirectoryService;
        this.automationRunRepository = automationRunRepository;
        this.automationRunItemRepository = automationRunItemRepository;
    }

    public Map<String, Object> loadStatus(String username) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("settings", settingsService.load().toMap());
        payload.put("credentials", credentialService.loadClientView(username));
        payload.put("bitrix_connection", bitrix24RestService.loadConnectionStatus(username));
        payload.put("iiko_connection", iikoDirectoryService.loadStatus(username));
        return payload;
    }

    public EmployeeDiscountAutomationSettings saveSettings(Map<String, Object> payload) {
        return settingsService.save(payload != null ? payload : Map.of());
    }

    public Map<String, Object> saveCredentials(String username, Map<String, Object> payload) {
        return credentialService.saveForUser(username, payload != null ? payload : Map.of()).toClientMap(username);
    }

    public List<Map<String, Object>> listBitrixGroups(String username, String query, Integer limit) {
        return bitrix24RestService.listWorkgroups(username, query, limit != null ? limit : 25);
    }

    public List<Map<String, Object>> loadIikoOrganizations(String username) {
        return iikoDirectoryService.loadOrganizations(username);
    }

    public List<Map<String, Object>> loadIikoCategories(String username) {
        return iikoDirectoryService.loadCategories(username);
    }

    public List<Map<String, Object>> loadIikoWallets(String username) {
        return iikoDirectoryService.loadWallets(username);
    }

    public Map<String, Object> previewSelection(String username) {
        EmployeeDiscountAutomationSettings settings = settingsService.load();
        if (settings.bitrixGroupId() == null || settings.bitrixGroupId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Сначала задайте bitrix_group_id в настройках автоматизации.");
        }
        List<CandidateTask> candidates = collectCandidates(username, settings);
        return Map.of(
            "success", true,
            "group_id", settings.bitrixGroupId(),
            "items", candidates.stream().map(this::toCandidateMap).toList()
        );
    }

    public Map<String, Object> run(Boolean dryRunRequested, String actor) {
        return run(dryRunRequested, actor, null);
    }

    public Map<String, Object> run(Boolean dryRunRequested, String actor, List<String> selectedTaskIds) {
        String effectiveActor = requireActor(actor);
        EmployeeDiscountAutomationSettings settings = settingsService.load();
        Set<String> requestedTaskIds = selectedTaskIds != null ? normalizeTaskIds(selectedTaskIds) : null;
        boolean dryRun = dryRunRequested != null ? dryRunRequested : settings.dryRunByDefault();
        OffsetDateTime now = OffsetDateTime.now();

        AutomationRun run = new AutomationRun();
        run.setAutomationKey(AUTOMATION_KEY);
        run.setMode(dryRun ? "dry_run" : "execute");
        run.setStatus("running");
        run.setActor(effectiveActor);
        run.setStartedAt(now);
        run.setCreatedAt(now);
        automationRunRepository.save(run);

        if (settings.bitrixGroupId() == null || settings.bitrixGroupId() <= 0) {
            return failRun(run, "Конфигурация автоматизации", "Сначала задайте bitrix_group_id в настройках автоматизации.");
        }

        List<CandidateTask> candidates;
        try {
            candidates = collectCandidates(effectiveActor, settings);
        } catch (Exception ex) {
            return failRun(run, "Bitrix24 discovery", integrationErrorMessage(ex, "Не удалось загрузить задачи Bitrix24."));
        }

        if (requestedTaskIds != null) {
            Set<String> discoveredTaskIds = new LinkedHashSet<>();
            for (CandidateTask candidate : candidates) {
                if (StringUtils.hasText(candidate.taskId())) {
                    discoveredTaskIds.add(candidate.taskId());
                }
            }
            List<String> missingTaskIds = requestedTaskIds.stream()
                .filter(taskId -> !discoveredTaskIds.contains(taskId))
                .toList();
            if (!missingTaskIds.isEmpty()) {
                return failRun(
                    run,
                    "Выбор задач",
                    "Выбранные задачи больше не найдены в текущем Bitrix preview: " + String.join(", ", missingTaskIds) + ". Обновите Preview перед запуском."
                );
            }
        }

        int successCount = 0;
        int errorCount = 0;
        int skippedCount = 0;
        int ignoredCount = 0;
        List<Map<String, Object>> itemPayloads = new ArrayList<>();

        for (CandidateTask candidate : candidates) {
            if ("ignored".equals(candidate.status())) {
                ignoredCount++;
                saveRunItem(run, candidate.taskId(), candidate.title(), candidate.phone(), "ignored", candidate.message(), candidate.checklistItemId());
                itemPayloads.add(buildItemPayload(candidate, "ignored", candidate.message()));
                continue;
            }
            if ("error".equals(candidate.status())) {
                errorCount++;
                saveRunItem(run, candidate.taskId(), candidate.title(), candidate.phone(), "error", candidate.message(), candidate.checklistItemId());
                itemPayloads.add(buildItemPayload(candidate, "error", candidate.message()));
                continue;
            }
            if (!"selected".equals(candidate.status())) {
                skippedCount++;
                saveRunItem(run, candidate.taskId(), candidate.title(), candidate.phone(), "skipped", candidate.message(), candidate.checklistItemId());
                itemPayloads.add(buildItemPayload(candidate, "skipped", candidate.message()));
                continue;
            }

            if (requestedTaskIds != null && !requestedTaskIds.contains(candidate.taskId())) {
                skippedCount++;
                String message = "Задача снята оператором в Preview и не будет отправлена в iiko.";
                saveRunItem(run, candidate.taskId(), candidate.title(), candidate.phone(), "skipped", message, candidate.checklistItemId());
                itemPayloads.add(buildItemPayload(candidate, "skipped", message));
                continue;
            }

            if (dryRun) {
                successCount++;
                saveRunItem(run, candidate.taskId(), candidate.title(), candidate.phone(), "dry_run", "Dry-run: задача готова к обработке.", candidate.checklistItemId());
                itemPayloads.add(buildItemPayload(candidate, "dry_run", "Dry-run: задача готова к обработке."));
                continue;
            }

            AutomationRunItem pendingItem = saveRunItem(
                run,
                candidate.taskId(),
                candidate.title(),
                candidate.phone(),
                "running",
                "Запущена обработка iiko; checklist Bitrix24 будет отмечен только после подтвержденного успеха.",
                candidate.checklistItemId()
            );
            try {
                IikoDirectoryService.MutationResult iikoResult = iikoDirectoryService.disableCorporateDiscount(effectiveActor, candidate.phone());
                if (!iikoResult.success()) {
                    errorCount++;
                    updateRunItem(pendingItem, "error", iikoResult.message());
                    itemPayloads.add(buildItemPayload(candidate, "error", iikoResult.message()));
                    continue;
                }

                bitrix24RestService.completeChecklistItem(effectiveActor, candidate.taskId(), candidate.checklistItemId());
                successCount++;
                String message = "Чеклист Bitrix24 отмечен после успешной обработки iiko.";
                updateRunItem(pendingItem, "success", message);
                itemPayloads.add(buildItemPayload(candidate, "success", message));
            } catch (Exception ex) {
                errorCount++;
                String message = integrationErrorMessage(ex, "Неизвестная ошибка интеграции.");
                updateRunItem(pendingItem, "error", message);
                itemPayloads.add(buildItemPayload(candidate, "error", message));
            }
        }

        run.setFinishedAt(OffsetDateTime.now());
        run.setStatus(errorCount > 0 ? (successCount > 0 ? "partial" : "error") : "success");
        run.setSummary(buildRunSummary(successCount, errorCount, skippedCount, ignoredCount));
        automationRunRepository.save(run);

        return Map.of(
            "success", true,
            "run", toRunMap(run),
            "items", itemPayloads
        );
    }

    public List<Map<String, Object>> listRuns(String actor) {
        String effectiveActor = requireActor(actor);
        return automationRunRepository
            .findTop20ByAutomationKeyAndActorIgnoreCaseOrderByStartedAtDesc(AUTOMATION_KEY, effectiveActor)
            .stream()
            .map(this::toRunMap)
            .toList();
    }

    public Map<String, Object> getRun(Long runId, String actor) {
        String effectiveActor = requireActor(actor);
        AutomationRun run = automationRunRepository
            .findByIdAndAutomationKeyAndActorIgnoreCase(runId, AUTOMATION_KEY, effectiveActor)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Run not found"));
        List<Map<String, Object>> items = automationRunItemRepository.findByRunIdOrderByCreatedAtAsc(runId).stream()
            .map(item -> Map.<String, Object>of(
                "id", item.getId(),
                "task_id", safe(item.getExternalTaskId()),
                "title", safe(item.getTaskTitle()),
                "phone", safe(item.getPhone()),
                "status", safe(item.getStatus()),
                "message", safe(item.getMessage()),
                "checklist_item_id", safe(item.getChecklistItemId()),
                "created_at", String.valueOf(item.getCreatedAt())
            ))
            .toList();
        return Map.of(
            "success", true,
            "run", toRunMap(run),
            "items", items
        );
    }

    private List<CandidateTask> collectCandidates(String username, EmployeeDiscountAutomationSettings settings) {
        List<Map<String, Object>> tasks = bitrix24RestService.listTasksForGroup(username, settings.bitrixGroupId());
        List<CandidateTask> candidates = new ArrayList<>();
        Pattern phonePattern = compilePattern(settings.phoneRegex());
        for (Map<String, Object> task : tasks) {
            String taskId = safe(task.get("id"));
            String title = safe(task.get("title"));
            String description = safe(task.get("description"));
            String status = safe(task.get("status"));
            if (isClosedTask(status, safe(task.get("closed_date")))) {
                candidates.add(new CandidateTask(taskId, title, "", "", "skipped", "Задача уже закрыта."));
                continue;
            }
            if (!matchesTitleMarkers(title, settings.taskTitleMarkers())) {
                candidates.add(new CandidateTask(taskId, title, "", "", "skipped", "Задача не попала под фильтр title markers."));
                continue;
            }
            List<Map<String, Object>> checklistItems = bitrix24RestService.listChecklistItems(username, taskId);
            ChecklistMatch checklist = findChecklistItem(checklistItems, settings.checklistLabels());
            if (checklist == null) {
                candidates.add(new CandidateTask(taskId, title, "", "", "skipped", "Не найден целевой checklist-пункт."));
                continue;
            }
            if (checklist.complete()) {
                candidates.add(new CandidateTask(taskId, title, "", checklist.itemId(), "skipped", "Checklist-пункт уже отмечен."));
                continue;
            }
            PhoneExtraction phone = extractPhone(description, phonePattern);
            if (!phone.valid()) {
                candidates.add(new CandidateTask(taskId, title, "", checklist.itemId(), "error", phone.message()));
                continue;
            }
            if (settings.ignoredPhoneNumbers().contains(phone.phone())) {
                candidates.add(new CandidateTask(
                    taskId,
                    title,
                    phone.phone(),
                    checklist.itemId(),
                    "ignored",
                    "Номер " + phone.phone() + " находится в ignore-list. iiko и checklist Bitrix24 не изменяются."
                ));
                continue;
            }
            candidates.add(new CandidateTask(taskId, title, phone.phone(), checklist.itemId(), "selected", "Задача готова к обработке."));
        }
        return candidates;
    }

    private Map<String, Object> buildItemPayload(CandidateTask candidate, String status, String message) {
        return Map.of(
            "task_id", candidate.taskId(),
            "title", candidate.title(),
            "phone", candidate.phone(),
            "status", status,
            "message", message
        );
    }

    private AutomationRunItem saveRunItem(AutomationRun run,
                                          String taskId,
                                          String title,
                                          String phone,
                                          String status,
                                          String message,
                                          String checklistItemId) {
        AutomationRunItem item = new AutomationRunItem();
        item.setRun(run);
        item.setExternalTaskId(taskId);
        item.setTaskTitle(title);
        item.setPhone(phone);
        item.setStatus(status);
        item.setMessage(message);
        item.setChecklistItemId(checklistItemId);
        item.setCreatedAt(OffsetDateTime.now());
        return automationRunItemRepository.save(item);
    }

    private void updateRunItem(AutomationRunItem item, String status, String message) {
        item.setStatus(status);
        item.setMessage(message);
        automationRunItemRepository.save(item);
    }

    private Map<String, Object> failRun(AutomationRun run, String title, String message) {
        saveRunItem(run, "", title, "", "error", message, "");
        run.setFinishedAt(OffsetDateTime.now());
        run.setStatus("error");
        run.setSummary("success=0, error=1, skipped=0");
        automationRunRepository.save(run);
        return Map.of(
            "success", false,
            "error", message,
            "run", toRunMap(run),
            "items", List.of(Map.of(
                "task_id", "",
                "title", title,
                "phone", "",
                "status", "error",
                "message", message
            ))
        );
    }

    private String integrationErrorMessage(Exception ex, String fallback) {
        if (ex instanceof ResponseStatusException responseStatusException
            && StringUtils.hasText(responseStatusException.getReason())) {
            return responseStatusException.getReason();
        }
        return StringUtils.hasText(ex.getMessage()) ? ex.getMessage() : fallback;
    }

    private Map<String, Object> toRunMap(AutomationRun run) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", run.getId());
        payload.put("automation_key", run.getAutomationKey());
        payload.put("mode", run.getMode());
        payload.put("status", run.getStatus());
        payload.put("actor", run.getActor());
        payload.put("summary", run.getSummary());
        payload.put("started_at", String.valueOf(run.getStartedAt()));
        payload.put("finished_at", String.valueOf(run.getFinishedAt()));
        payload.put("created_at", String.valueOf(run.getCreatedAt()));
        return payload;
    }

    private Map<String, Object> toCandidateMap(CandidateTask candidate) {
        return Map.of(
            "task_id", candidate.taskId(),
            "title", candidate.title(),
            "phone", candidate.phone(),
            "checklist_item_id", candidate.checklistItemId(),
            "status", candidate.status(),
            "message", candidate.message()
        );
    }

    private Pattern compilePattern(String regex) {
        try {
            return Pattern.compile(StringUtils.hasText(regex) ? regex : "(?iu)тел\\.?\\s*сотрудника\\s*[:\\-]?\\s*([+\\d\\s()\\-]{10,})");
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный phone_regex: " + ex.getMessage(), ex);
        }
    }

    private static final Pattern GENERIC_RUSSIAN_PHONE_PATTERN = Pattern.compile(
        "(?<!\\d)(?:\\+?7|8)[\\s()\\-]*\\d{3}[\\s()\\-]*\\d{3}[\\s\\-]*\\d{2}[\\s\\-]*\\d{2}(?!\\d)"
    );

    private PhoneExtraction extractPhone(String rawDescription, Pattern configuredPattern) {
        if (!StringUtils.hasText(rawDescription)) {
            return PhoneExtraction.error("Не удалось извлечь телефон сотрудника: тело задачи пустое.");
        }
        String description = rawDescription
            .replaceAll("<[^>]+>", " ")
            .replace("&nbsp;", " ")
            .replaceAll("\\s+", " ")
            .trim();

        Matcher configuredMatcher = configuredPattern.matcher(description);
        if (configuredMatcher.find()) {
            String value = configuredMatcher.groupCount() >= 1
                ? configuredMatcher.group(1)
                : configuredMatcher.group();
            String normalized = EmployeeDiscountAutomationSettingsService.normalizeRussianPhone(value);
            if (StringUtils.hasText(normalized)) {
                return PhoneExtraction.ok(normalized);
            }
        }

        LinkedHashSet<String> fallbackPhones = new LinkedHashSet<>();
        Matcher fallbackMatcher = GENERIC_RUSSIAN_PHONE_PATTERN.matcher(description);
        while (fallbackMatcher.find()) {
            String normalized = EmployeeDiscountAutomationSettingsService.normalizeRussianPhone(fallbackMatcher.group());
            if (StringUtils.hasText(normalized)) {
                fallbackPhones.add(normalized);
            }
        }
        if (fallbackPhones.size() == 1) {
            return PhoneExtraction.ok(fallbackPhones.iterator().next());
        }
        if (fallbackPhones.size() > 1) {
            return PhoneExtraction.error(
                "В задаче найдено несколько разных телефонных номеров: "
                    + String.join(", ", fallbackPhones)
                    + ". Уточните phone_regex или данные задачи."
            );
        }
        return PhoneExtraction.error("Не удалось извлечь корректный российский телефон сотрудника из тела задачи.");
    }

    private Set<String> normalizeTaskIds(List<String> selectedTaskIds) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        if (selectedTaskIds == null) {
            return result;
        }
        for (String taskId : selectedTaskIds) {
            String normalized = safe(taskId);
            if (StringUtils.hasText(normalized)) {
                result.add(normalized);
            }
        }
        return result;
    }

    private String buildRunSummary(int successCount, int errorCount, int skippedCount, int ignoredCount) {
        String summary = "success=" + successCount + ", error=" + errorCount + ", skipped=" + skippedCount;
        if (ignoredCount > 0) {
            summary += ", ignored=" + ignoredCount;
        }
        return summary;
    }

    private boolean matchesTitleMarkers(String title, List<String> markers) {
        if (markers == null || markers.isEmpty()) {
            return true;
        }
        if (!StringUtils.hasText(title)) {
            return false;
        }
        String normalizedTitle = title.toLowerCase(Locale.ROOT);
        for (String marker : markers) {
            if (StringUtils.hasText(marker) && normalizedTitle.contains(marker.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private ChecklistMatch findChecklistItem(List<Map<String, Object>> checklistItems, List<String> labels) {
        if (checklistItems == null || checklistItems.isEmpty() || labels == null || labels.isEmpty()) {
            return null;
        }
        List<String> normalizedLabels = labels.stream()
            .filter(StringUtils::hasText)
            .map(value -> value.trim().toLowerCase(Locale.ROOT))
            .toList();
        for (Map<String, Object> item : checklistItems) {
            String title = safe(item.get("title"));
            if (!StringUtils.hasText(title)) {
                continue;
            }
            if (!normalizedLabels.contains(title.trim().toLowerCase(Locale.ROOT))) {
                continue;
            }
            return new ChecklistMatch(
                safe(item.get("id")),
                Boolean.TRUE.equals(item.get("is_complete"))
            );
        }
        return null;
    }

    private boolean isClosedTask(String status, String closedDate) {
        if (StringUtils.hasText(closedDate)) {
            return true;
        }
        String normalized = StringUtils.hasText(status) ? status.trim().toLowerCase(Locale.ROOT) : "";
        return "5".equals(normalized)
            || "7".equals(normalized)
            || "completed".equals(normalized)
            || "closed".equals(normalized)
            || "done".equals(normalized);
    }

    private String requireActor(String actor) {
        if (!StringUtils.hasText(actor)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Не удалось определить пользователя панели.");
        }
        return actor.trim();
    }

    private String safe(Object value) {
        return value != null ? String.valueOf(value).trim() : "";
    }

    private record PhoneExtraction(String phone, String message) {
        private static PhoneExtraction ok(String phone) {
            return new PhoneExtraction(phone, "");
        }

        private static PhoneExtraction error(String message) {
            return new PhoneExtraction("", message);
        }

        private boolean valid() {
            return StringUtils.hasText(phone);
        }
    }

    private record CandidateTask(String taskId,
                                 String title,
                                 String phone,
                                 String checklistItemId,
                                 String status,
                                 String message) {
    }

    private record ChecklistMatch(String itemId, boolean complete) {
    }
}
