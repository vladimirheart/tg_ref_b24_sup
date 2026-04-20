# 2026-04-20 16:57:00 — dialog workspace client profile and context blocks split

## Что сделано

- Продолжен service-level split giant `DialogWorkspaceService`.
- Вынесен `DialogWorkspaceClientProfileService`:
  сегментация клиента и `profile_health` больше не собираются внутри основного
  workspace service.
- Вынесен `DialogWorkspaceContextBlockService`:
  `context blocks` и `blocks health` больше не живут в giant service.
- `DialogWorkspaceService` переведён на делегирование новых sub-services без
  смены API-контракта.
- Из giant service удалён уже неиспользуемый хвост, связанный с этими
  срезами, чтобы разгрузка была реальной, а не дублирующей.
- Добавлены targeted unit tests:
  `DialogWorkspaceClientProfileServiceTest` и
  `DialogWorkspaceContextBlockServiceTest`.
- Обновлены `ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`,
  `ARCHITECTURE_AUDIT_2026-04-08.md` и task detail `01-024`.

## Зачем

Это ещё один практический шаг к тому, чтобы `DialogWorkspaceService` постепенно
сжимался до orchestration-слоя, а не продолжал держать в себе и клиентскую
сегментацию, и profile-health policy, и context-block readiness одновременно.

## Проверка

- `spring-panel\mvnw.cmd -q clean`
- `spring-panel\mvnw.cmd -q "-Dtest=DialogWorkspaceClientProfileServiceTest,DialogWorkspaceContextBlockServiceTest,DialogWorkspaceNavigationServiceTest,DialogWorkspaceRolloutServiceTest,DialogWorkspaceParityServiceTest" test`
- `spring-panel\mvnw.cmd -q "-Dtest=DialogWorkspaceControllerWebMvcTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`
