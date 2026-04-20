# 2026-04-20 16:34:00 — dialog workspace navigation and rollout split

## Что сделано

- Продолжен service-level split giant `DialogWorkspaceService`.
- Вынесен `DialogWorkspaceNavigationService`:
  queue/navigation meta для workspace больше не собирается внутри основного
  workspace service.
- Вынесен `DialogWorkspaceRolloutService`:
  rollout/meta-config пакет по `workspace_v1`, `single_mode`, legacy fallback и
  policy legacy manual open больше не живёт в giant service.
- `DialogWorkspaceService` переведён на делегирование в новые sub-services без
  смены API-контракта `DialogWorkspaceController`.
- Добавлены targeted unit tests:
  `DialogWorkspaceNavigationServiceTest` и
  `DialogWorkspaceRolloutServiceTest`.
- Обновлены `ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`,
  `ARCHITECTURE_AUDIT_2026-04-08.md` и task detail `01-024`.

## Зачем

Это ещё один практический шаг к тому, чтобы `DialogWorkspaceService` перестал
быть вторым giant-hotspot после `DialogService`. После выноса navigation и
rollout/meta-config service стал ближе к orchestration-слою, а не к месту, где
держатся и UI queue logic, и rollout policy parsing одновременно.

## Проверка

- `spring-panel\mvnw.cmd -q "-Dtest=DialogWorkspaceNavigationServiceTest,DialogWorkspaceRolloutServiceTest,DialogWorkspaceParityServiceTest,DialogWorkspaceControllerWebMvcTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`
