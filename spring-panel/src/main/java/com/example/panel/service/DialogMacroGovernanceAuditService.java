package com.example.panel.service;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class DialogMacroGovernanceAuditService {

    private final DialogMacroGovernanceConfigService dialogMacroGovernanceConfigService;
    private final DialogMacroGovernanceTemplateAuditService dialogMacroGovernanceTemplateAuditService;
    private final DialogMacroGovernanceCheckpointService dialogMacroGovernanceCheckpointService;
    private final DialogMacroGovernanceAuditPayloadService dialogMacroGovernanceAuditPayloadService;

    public DialogMacroGovernanceAuditService(DialogMacroGovernanceConfigService dialogMacroGovernanceConfigService,
                                             DialogMacroGovernanceTemplateAuditService dialogMacroGovernanceTemplateAuditService,
                                             DialogMacroGovernanceCheckpointService dialogMacroGovernanceCheckpointService,
                                             DialogMacroGovernanceAuditPayloadService dialogMacroGovernanceAuditPayloadService) {
        this.dialogMacroGovernanceConfigService = dialogMacroGovernanceConfigService;
        this.dialogMacroGovernanceTemplateAuditService = dialogMacroGovernanceTemplateAuditService;
        this.dialogMacroGovernanceCheckpointService = dialogMacroGovernanceCheckpointService;
        this.dialogMacroGovernanceAuditPayloadService = dialogMacroGovernanceAuditPayloadService;
    }

    public Map<String, Object> buildAudit(Map<String, Object> settings) {
        DialogMacroGovernanceConfigService.AuditConfig config = dialogMacroGovernanceConfigService.resolve(settings);
        DialogMacroGovernanceTemplateAuditService.TemplateAuditBundle templateAudit =
                dialogMacroGovernanceTemplateAuditService.audit(config);
        DialogMacroGovernanceCheckpointService.CheckpointBundle checkpoints =
                dialogMacroGovernanceCheckpointService.evaluate(config, templateAudit.missingOwnerTotal());
        return dialogMacroGovernanceAuditPayloadService.build(config, templateAudit, checkpoints);
    }
}
