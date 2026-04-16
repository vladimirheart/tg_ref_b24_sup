package com.example.panel.service;

import com.example.panel.entity.AutomationRun;
import com.example.panel.entity.AutomationRunItem;
import com.example.panel.repository.AutomationRunItemRepository;
import com.example.panel.repository.AutomationRunRepository;
import com.example.panel.service.EmployeeDiscountAutomationSettingsService.EmployeeDiscountAutomationSettings;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class EmployeeDiscountAutomationService {

    public static final String AUTOMATION_KEY = "employee_discount_automation";

    private final EmployeeDiscountAutomationSettingsService settingsService;
    private final LocalMachineIntegrationsConfigService localMachineIntegrationsConfigService;
    private final Bitrix24RestService bitrix24RestService;
    private final IikoDirectoryService iikoDirectoryService;
    private final AutomationRunRepository automationRunRepository;
    private final AutomationRunItemRepository automationRunItemRepository;

    public EmployeeDiscountAutomationService(EmployeeDiscountAutomationSettingsService settingsService,
                                             LocalMachineIntegrationsConfigService localMachineIntegrationsConfigService,
                                             Bitrix24RestService bitrix24RestService,
                                             IikoDirectoryService iikoDirectoryService,
                                             AutomationRunRepository automationRunRepository,
                                             AutomationRunItemRepository automationRunItemRepository) {
        this.settingsService = settingsService;
        this.localMachineIntegrationsConfigService = localMachineIntegrationsConfigService;
        this.bitrix24RestService = bitrix24RestService;
        this.iikoDirectoryService = iikoDirectoryService;
        this.automationRunRepository = automationRunRepository;
        this.automationRunItemRepository = automationRunItemRepository;
    }

    public Map<String, Object> loadStatus() {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("settings", settingsService.load().toMap());
        payload.put("local_config", localMachineIntegrationsConfigService.loadStatus());
        payload.put("bitrix_connection", bitrix24RestService.loadConnectionStatus());
        payload.put("iiko_connection", iikoDirectoryService.loadStatus());
        return payload;
    }

    public EmployeeDiscountAutomationSettings saveSettings(Map<String, Object> payload) {
        return settingsService.save(payload != null ? payload : Map.of());
    }

    public List<Map<String, Object>> listBitrixGroups(String query, Integer limit) {
        return bitrix24RestService.listWorkgroups(query, limit != null ? limit : 25);
    }

    public List<Map<String, Object>> loadIikoCategories() {
        return iikoDirectoryService.loadCategories();
    }

    public List<Map<String, Object>> loadIikoWallets() {
        return iikoDirectoryService.loadWallets();
    }

    public Map<String, Object> previewSelection() {
        EmployeeDiscountAutomationSettings settings = settingsService.load();
        if (settings.bitrixGroupId() == null || settings.bitrixGroupId() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Сначала задайте bitrix_group_id в настройках автоматизации.");
        }
        List<CandidateTask> candidates = collectCandidates(settings);
        return Map.of(
            "success", true,
            "group_id", settings.bitrixGroupId(),
            "items", candidates.stream().map(this::toCandidateMap).toList()
        );
    }

    @Transactional
    public Map<String, Object> run(Boolean dryRunRequested, String actor) {
        EmployeeDiscountAutomationSettings settings = settingsService.load();
        boolean dryRun = dryRunRequested != null ? dryRunRequested : settings.dryRunByDefault();
        List<CandidateTask> candidates = collectCandidates(settings);
        OffsetDateTime now = OffsetDateTime.now();

        AutomationRun run = new AutomationRun();
        run.setAutomationKey(AUTOMATION_KEY);
        run.setMode(dryRun ? "dry_run" : "execute");
        run.setStatus("running");
        run.setActor(actor);
        run.setStartedAt(now);
        run.setCreatedAt(now);
        automationRunRepository.save(run);

        int successCount = 0;
        int errorCount = 0;
        int skippedCount = 0;
        List<Map<String, Object>> itemPayloads = new ArrayList<>();

        for (CandidateTask candidate : candidates) {
            if (!"selected".equals(candidate.status())) {
                skippedCount++;
                saveRunItem(run, candidate.taskId(), candidate.title(), candidate.phone(), "skipped", candidate.message(), candidate.checklistItemId());
                itemPayloads.add(buildItemPayload(candidate, "skipped", candidate.message()));
                continue;
            }

            if (dryRun) {
                successCount++;
                saveRunItem(run, candidate.taskId(), candidate.title(), candidate.phone(), "dry_run", "Dry-run: задача готова к обработке.", candidate.checklistItemId());
                itemPayloads.add(buildItemPayload(candidate, "dry_run", "Dry-run: задача готова к обработке."));
                continue;
            }

            IikoDirectoryService.MutationResult iikoResult = iikoDirectoryService.disableCorporateDiscount(candidate.phone(), settings);
            if (!iikoResult.success()) {
                errorCount++;
                saveRunItem(run, candidate.taskId(), candidate.title(), candidate.phone(), "error", iikoResult.message(), candidate.checklistItemId());
                itemPayloads.add(buildItemPayload(candidate, "error", iikoResult.message()));
                continue;
            }

            bitrix24RestService.completeChecklistItem(candidate.taskId(), candidate.checklistItemId());
            successCount++;
            saveRunItem(run, candidate.taskId(), candidate.title(), candidate.phone(), "success", "Чеклист Bitrix24 отмечен после успешной обработки iiko.", candidate.checklistItemId());
            itemPayloads.add(buildItemPayload(candidate, "success", "Чеклист Bitrix24 отмечен после успешной обработки iiko."));
        }

        run.setFinishedAt(OffsetDateTime.now());
        run.setStatus(errorCount > 0 ? (successCount > 0 ? "partial" : "error") : "success");
        run.setSummary("success=" + successCount + ", error=" + errorCount + ", skipped=" + skippedCount);
        automationRunRepository.save(run);

        return Map.of(
            "success", true,
            "run", toRunMap(run),
            "items", itemPayloads
        );
    }

    public List<Map<String, Object>> listRuns() {
        return automationRunRepository.findTop20ByAutomationKeyOrderByStartedAtDesc(AUTOMATION_KEY).stream()
            .map(this::toRunMap)
            .toList();
    }

    public Map<String, Object> getRun(Long runId) {
        AutomationRun run = automationRunRepository.findById(runId)
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

    private List<CandidateTask> collectCandidates(EmployeeDiscountAutomationSettings settings) {
        List<Map<String, Object>> tasks = bitrix24RestService.listTasksForGroup(settings.bitrixGroupId());
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
            List<Map<String, Object>> checklistItems = bitrix24RestService.listChecklistItems(taskId);
            ChecklistMatch checklist = findChecklistItem(checklistItems, settings.checklistLabels());
            if (checklist == null) {
                candidates.add(new CandidateTask(taskId, title, "", "", "skipped", "Не найден целевой checklist-пункт."));
                continue;
            }
            if (checklist.complete()) {
                candidates.add(new CandidateTask(taskId, title, "", checklist.itemId(), "skipped", "Checklist-пункт уже отмечен."));
                continue;
            }
            String phone = extractPhone(description, phonePattern);
            if (!StringUtils.hasText(phone)) {
                candidates.add(new CandidateTask(taskId, title, "", checklist.itemId(), "error", "Не удалось извлечь телефон сотрудника из тела задачи."));
                continue;
            }
            candidates.add(new CandidateTask(taskId, title, phone, checklist.itemId(), "selected", "Задача готова к обработке."));
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

    private void saveRunItem(AutomationRun run,
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
        automationRunItemRepository.save(item);
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

    private String extractPhone(String rawDescription, Pattern pattern) {
        if (!StringUtils.hasText(rawDescription)) {
            return "";
        }
        String description = rawDescription
            .replaceAll("<[^>]+>", " ")
            .replace("&nbsp;", " ")
            .replaceAll("\\s+", " ")
            .trim();
        Matcher matcher = pattern.matcher(description);
        if (!matcher.find()) {
            return "";
        }
        String value = matcher.groupCount() >= 1 ? matcher.group(1) : matcher.group();
        return normalizePhone(value);
    }

    private String normalizePhone(String rawPhone) {
        if (!StringUtils.hasText(rawPhone)) {
            return "";
        }
        String digits = rawPhone.trim().replaceAll("[^\\d+]", "");
        if (digits.startsWith("8") && digits.length() == 11) {
            return "+7" + digits.substring(1);
        }
        if (!digits.startsWith("+") && digits.length() == 11 && digits.startsWith("7")) {
            return "+" + digits;
        }
        if (!digits.startsWith("+") && digits.length() == 10) {
            return "+7" + digits;
        }
        return digits;
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
            || "6".equals(normalized)
            || "7".equals(normalized)
            || "completed".equals(normalized)
            || "closed".equals(normalized)
            || "done".equals(normalized);
    }

    private String safe(Object value) {
        return value != null ? String.valueOf(value).trim() : "";
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
