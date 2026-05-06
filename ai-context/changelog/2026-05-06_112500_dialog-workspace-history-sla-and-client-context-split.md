## 2026-05-06 11:25 - Dialog workspace history, SLA and client-context split

- added `DialogWorkspaceHistorySliceService` for cursor/limit based message
  pagination;
- added `DialogWorkspaceSlaViewService` for workspace SLA/runtime envelope,
  deadline/state/minutes-left and routing policy snapshot assembly;
- added `DialogWorkspaceClientContextAssemblerService` for external profile
  enrichment, client payload assembly, profile-match candidates, context
  sources, context blocks and context contract composition;
- refactored `DialogWorkspaceService` into a thinner orchestration facade over
  history, context and SLA bounded services;
- added targeted tests for the new workspace history/SLA/context services;
- updated architecture audit, roadmap and task detail `01-024` to reflect the
  new workspace baseline and the shift of remaining risk into compact
  context-heavy bounded services.
