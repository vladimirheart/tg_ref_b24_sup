# 2026-04-22 16:07 — dialog service decoupling support and resolve result pass

## Что сделано

- вынесен общий helper `DialogDataAccessSupport` вместо nested static helper в
  `DialogService`;
- вынесен `DialogResolveResult` вместо nested record `DialogService`;
- на новые опоры переведены `DialogTicketLifecycleService`,
  `DialogQuickActionService`, `DialogQuickActionsController`,
  `DialogAuditService`, `DialogLookupReadService`,
  `DialogConversationReadService`, `DialogClientContextReadService` и
  `DialogResponsibilityService`;
- синхронизированы targeted tests вокруг `dialogs` после снятия nested/static
  coupling;
- актуализированы `01-024`, roadmap и архитектурный аудит под новый шаг
  service-level split.

## Проверка

- `spring-panel\mvnw.cmd -q "-Dtest=DialogServiceTest,DialogTicketLifecycleServiceTest,DialogQuickActionsControllerWebMvcTest,DialogAuditServiceTest,DialogLookupReadServiceTest,DialogConversationReadServiceTest,DialogClientContextReadServiceTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Результат

- direct coupling к nested/static API `DialogService` уменьшен;
- `DialogService` стал чуть уже и чище как giant service boundary;
- следующий логичный шаг по аудиту: снимать оставшиеся direct dependencies для
  `DialogWorkspaceTelemetrySummaryService` и `DialogMacroGovernanceAuditService`.
