# 2026-04-20 17:35:00 — dialog workspace client payload and context source split

## Что сделано

- Продолжен service-level split giant `DialogWorkspaceService`.
- Вынесен `DialogWorkspaceClientPayloadService`:
  filtering profile enrichment, external links и client attribute config больше
  не собираются внутри основного workspace service.
- Вынесен `DialogWorkspaceContextSourceService`:
  context sources и attribute policies больше не живут в giant service.
- `DialogWorkspaceService` переведён на делегирование новых sub-services без
  смены API-контракта.
- Из giant service удалён ещё один уже неиспользуемый хвост, связанный с
  client payload и source policy логикой.
- Добавлены targeted unit tests:
  `DialogWorkspaceClientPayloadServiceTest` и
  `DialogWorkspaceContextSourceServiceTest`.
- Обновлены `ARCH_UI_REFACTORING_ROADMAP_2026-04-15.md`,
  `ARCHITECTURE_AUDIT_2026-04-08.md` и task detail `01-024`.

## Зачем

Это следующий практический шаг к тому, чтобы `DialogWorkspaceService`
сжимался до orchestration-слоя. После выноса client payload support и source
policy giant service уже меньше держит в себе и конфигурирование внешних
ссылок/атрибутов клиента, и source/freshness policy assembly одновременно.

## Проверка

- `spring-panel\mvnw.cmd -q clean`
- `spring-panel\mvnw.cmd -q "-Dtest=DialogWorkspaceClientPayloadServiceTest,DialogWorkspaceContextSourceServiceTest,DialogWorkspaceClientProfileServiceTest,DialogWorkspaceContextBlockServiceTest,DialogWorkspaceNavigationServiceTest,DialogWorkspaceRolloutServiceTest,DialogWorkspaceParityServiceTest" test`
- `spring-panel\mvnw.cmd -q "-Dtest=DialogWorkspaceControllerWebMvcTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`
