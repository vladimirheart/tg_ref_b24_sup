# 2026-04-21 15:23 — dialog lookup and responsibility service split

## Что сделано

- добавлен `DialogLookupReadService` для `loadSummary`, `loadDialogs` и
  `findDialog`;
- добавлен `DialogResponsibilityService` для `assignResponsibleIfMissing`,
  `markDialogAsRead` и `assignResponsibleIfMissingOrRedirected`;
- `DialogService` переведён на thin delegates для новых lookup/responsibility
  сценариев;
- на новые зависимости переведены:
  `DialogListReadService`, `DialogWorkspaceNavigationService`,
  `DialogWorkspaceTelemetryService`, `DialogQuickActionService`,
  `DialogReadService`, `DialogWorkspaceService`, `DialogReplyService`,
  `DashboardController`, `DashboardApiController`, `DialogsController`;
- синхронизированы unit/WebMvc tests для list/navigation/dashboard/dialogs;
- добавлены `DialogLookupReadServiceTest` и
  `DialogResponsibilityServiceTest`;
- обновлены `ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`,
  `ARCHITECTURE_AUDIT_2026-04-08.md` и `01-024.md`.

## Проверка

- `spring-panel\.\mvnw.cmd -q -DskipTests compile`
- `spring-panel\.\mvnw.cmd -q "-Dtest=DialogLookupReadServiceTest,DialogResponsibilityServiceTest,DialogListReadServiceTest,DialogWorkspaceNavigationServiceTest,DashboardControllerWebMvcTest,DialogsControllerWebMvcTest" test`
