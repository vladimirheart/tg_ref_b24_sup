# 2026-04-22 16:15 — dialog service support and result decoupling pass

## Что сделано

- вынесен `DialogDataAccessSupport` вместо nested static helper в
  `DialogService`;
- вынесен `DialogResolveResult` вместо nested record giant service;
- на новые опоры переведены `DialogTicketLifecycleService`,
  `DialogQuickActionService`, `DialogQuickActionsController`,
  `DialogAuditService`, `DialogLookupReadService`,
  `DialogConversationReadService`, `DialogClientContextReadService` и
  `DialogResponsibilityService`;
- синхронизированы targeted tests вокруг `dialogs` после снятия nested/static
  coupling;
- обновлены `01-024`, roadmap и архитектурный аудит под новый этап
  service-level split.

## Проверка

- `spring-panel\mvnw.cmd -q "-Dtest=DialogServiceTest,DialogTicketLifecycleServiceTest,DialogQuickActionsControllerWebMvcTest,DialogAuditServiceTest,DialogLookupReadServiceTest,DialogConversationReadServiceTest,DialogClientContextReadServiceTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Результат

- direct coupling к nested/static API `DialogService` уменьшен;
- `DialogService` стал чуть уже и чище как giant service boundary;
- следующим логичным шагом остаётся вынос real logic из
  `DialogWorkspaceTelemetrySummaryService` и
  `DialogMacroGovernanceAuditService`, которые пока ещё держатся на direct
  dependency к giant service.
