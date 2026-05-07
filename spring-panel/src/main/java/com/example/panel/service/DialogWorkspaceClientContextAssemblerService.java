package com.example.panel.service;

import com.example.panel.model.dialog.DialogListItem;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;

@Service
public class DialogWorkspaceClientContextAssemblerService {

    private final DialogWorkspaceProfileEnrichmentService dialogWorkspaceProfileEnrichmentService;
    private final DialogWorkspaceClientCardService dialogWorkspaceClientCardService;
    private final DialogWorkspaceContextGraphService dialogWorkspaceContextGraphService;

    public DialogWorkspaceClientContextAssemblerService(DialogWorkspaceProfileEnrichmentService dialogWorkspaceProfileEnrichmentService,
                                                        DialogWorkspaceClientCardService dialogWorkspaceClientCardService,
                                                        DialogWorkspaceContextGraphService dialogWorkspaceContextGraphService) {
        this.dialogWorkspaceProfileEnrichmentService = dialogWorkspaceProfileEnrichmentService;
        this.dialogWorkspaceClientCardService = dialogWorkspaceClientCardService;
        this.dialogWorkspaceContextGraphService = dialogWorkspaceContextGraphService;
    }

    public WorkspaceClientContextBundle assemble(Map<String, Object> settings,
                                                 DialogListItem summary,
                                                 String ticketId,
                                                 List<Map<String, Object>> clientHistory,
                                                 List<Map<String, Object>> relatedEvents,
                                                 Map<String, Object> profileEnrichment) {
        DialogWorkspaceProfileEnrichmentService.ProfileEnrichmentBundle enrichmentBundle =
                dialogWorkspaceProfileEnrichmentService.resolve(settings, summary, ticketId, profileEnrichment);
        DialogWorkspaceClientCardService.WorkspaceClientCard clientCard =
                dialogWorkspaceClientCardService.build(settings, summary, ticketId, enrichmentBundle.filteredProfileEnrichment());
        DialogWorkspaceContextGraphService.ContextGraphBundle contextGraph =
                dialogWorkspaceContextGraphService.build(
                        settings,
                        summary,
                        clientCard.workspaceClient(),
                        clientCard.attributeLabels(),
                        enrichmentBundle.effectiveProfileEnrichment(),
                        enrichmentBundle.filteredProfileEnrichment(),
                        clientCard.externalLinks(),
                        clientHistory,
                        relatedEvents
                );

        return new WorkspaceClientContextBundle(
                contextGraph.workspaceClient(),
                clientHistory,
                contextGraph.profileMatchCandidates(),
                relatedEvents,
                contextGraph.profileHealth(),
                contextGraph.contextSources(),
                contextGraph.attributePolicies(),
                contextGraph.contextBlocks(),
                contextGraph.contextBlocksHealth(),
                contextGraph.contextContract()
        );
    }

    public record WorkspaceClientContextBundle(Map<String, Object> workspaceClient,
                                               List<Map<String, Object>> clientHistory,
                                               Map<String, Object> profileMatchCandidates,
                                               List<Map<String, Object>> relatedEvents,
                                               Map<String, Object> profileHealth,
                                               List<Map<String, Object>> contextSources,
                                               List<Map<String, Object>> attributePolicies,
                                               List<Map<String, Object>> contextBlocks,
                                               Map<String, Object> contextBlocksHealth,
                                               Map<String, Object> contextContract) {
    }
}
