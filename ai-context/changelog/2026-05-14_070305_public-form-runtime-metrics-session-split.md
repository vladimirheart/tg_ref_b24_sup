## Summary
- started bounded split of `PublicFormService` by extracting runtime config, metrics, and session flow services
- kept `DialogAiAssistantService` baseline intact and verified neighboring AI assistant regression coverage
- updated audit/roadmap/task-detail priorities to reflect real progress and remaining `PublicFormService` tail

## What Changed
- added `PublicFormRuntimeConfigService` for dialog config readers, locale/polling settings, payload limits, and disabled-status normalization
- added `PublicFormMetricsService` for config/submit/session metrics and alert snapshot assembly
- added `PublicFormSessionService` for session lookup, token rotation, and active-session parsing
- rewired `PublicFormService` to delegate these slices and reduced it from about `1327` to `1020` lines
- added `PublicFormRuntimeConfigServiceTest`, `PublicFormMetricsServiceTest`, and `PublicFormSessionServiceTest`
- revalidated `PublicFormApiControllerWebMvcTest`, `PublicFormControllerWebMvcTest`, `DialogAiAssistantEscalationServiceTest`, and `DialogAiAssistantMessageFlowServiceTest`

## Why It Matters
- `PublicFormService` is no longer a single mixed runtime/config/session/metrics bucket
- audit priorities now reflect that `PublicFormService` has already started splitting, so the remaining work is narrower and clearer
