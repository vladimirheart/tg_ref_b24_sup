# 2026-04-20 16:11:00 — dialog workspace subservices pass

## Что сделано

- Начат service-level split giant `DialogWorkspaceService`.
- Вынесен `DialogWorkspaceExternalProfileService`:
  fetch/cache/auth/normalize логика внешнего профиля клиента больше не живёт
  внутри основного workspace service.
- Вынесен `DialogWorkspaceParityService`:
  `composer` и `parity` assembly собраны в отдельном сервисе.
- Из `DialogWorkspaceService` убран legacy хвост после controller split:
  старые macro helper'ы и неиспользуемые request records для telemetry/macro/ai.
- Добавлен unit test `DialogWorkspaceParityServiceTest`.

## Зачем

Это первый реальный шаг не только по controller split, но и по сужению самого
`DialogWorkspaceService`. После него giant service уже меньше завязан на
внешние интеграции и UI parity-логику, а следующий split можно делать
пакетами, а не через один большой refactor.

## Проверка

- `spring-panel\mvnw.cmd -q "-Dtest=DialogWorkspaceParityServiceTest,DialogWorkspaceControllerWebMvcTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`
