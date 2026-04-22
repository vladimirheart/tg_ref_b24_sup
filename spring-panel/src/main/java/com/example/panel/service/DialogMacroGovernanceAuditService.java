package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class DialogMacroGovernanceAuditService {

    private final DialogService dialogService;

    public DialogMacroGovernanceAuditService(DialogService dialogService) {
        this.dialogService = dialogService;
    }

    public Map<String, Object> buildAudit(Map<String, Object> settings) {
        return dialogService.buildMacroGovernanceAudit(settings);
    }
}
