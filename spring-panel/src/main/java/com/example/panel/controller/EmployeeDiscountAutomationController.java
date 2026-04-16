package com.example.panel.controller;

import com.example.panel.service.EmployeeDiscountAutomationService;
import com.example.panel.service.EmployeeDiscountAutomationSettingsService.EmployeeDiscountAutomationSettings;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/ai-ops/employee-discounts")
@PreAuthorize("hasAuthority('PAGE_DIALOGS')")
public class EmployeeDiscountAutomationController {

    private final EmployeeDiscountAutomationService employeeDiscountAutomationService;

    public EmployeeDiscountAutomationController(EmployeeDiscountAutomationService employeeDiscountAutomationService) {
        this.employeeDiscountAutomationService = employeeDiscountAutomationService;
    }

    @GetMapping("/status")
    public Map<String, Object> status() {
        return Map.of("success", true, "status", employeeDiscountAutomationService.loadStatus());
    }

    @PostMapping("/settings")
    public Map<String, Object> saveSettings(@RequestBody(required = false) Map<String, Object> payload) {
        EmployeeDiscountAutomationSettings settings = employeeDiscountAutomationService.saveSettings(payload != null ? payload : Map.of());
        return Map.of("success", true, "settings", settings.toMap());
    }

    @GetMapping("/bitrix/groups")
    public Map<String, Object> groups(@RequestParam(value = "query", required = false) String query,
                                      @RequestParam(value = "limit", required = false) Integer limit) {
        List<Map<String, Object>> items = employeeDiscountAutomationService.listBitrixGroups(query, limit);
        return Map.of("success", true, "items", items);
    }

    @GetMapping("/preview")
    public Map<String, Object> preview() {
        return employeeDiscountAutomationService.previewSelection();
    }

    @GetMapping("/iiko/categories")
    public Map<String, Object> categories() {
        return Map.of("success", true, "items", employeeDiscountAutomationService.loadIikoCategories());
    }

    @GetMapping("/iiko/wallets")
    public Map<String, Object> wallets() {
        return Map.of("success", true, "items", employeeDiscountAutomationService.loadIikoWallets());
    }

    @PostMapping("/runs")
    public Map<String, Object> run(@RequestBody(required = false) Map<String, Object> payload,
                                   Authentication authentication) {
        Boolean dryRun = payload != null && payload.containsKey("dry_run")
            ? Boolean.valueOf(String.valueOf(payload.get("dry_run")))
            : null;
        String actor = authentication != null ? authentication.getName() : null;
        return employeeDiscountAutomationService.run(dryRun, actor);
    }

    @GetMapping("/runs")
    public Map<String, Object> runs() {
        return Map.of("success", true, "items", employeeDiscountAutomationService.listRuns());
    }

    @GetMapping("/runs/{runId}")
    public Map<String, Object> runDetails(@PathVariable Long runId) {
        return employeeDiscountAutomationService.getRun(runId);
    }
}
