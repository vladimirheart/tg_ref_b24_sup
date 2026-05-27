# 2026-05-27 12:20:18 — dialog list owner handoff and resolve lifecycle

## Что сделано
- `DialogDetailsIntegrationTest` расширен двумя live
  `SpringBootTest + SQLite` сценариями на `/api/dialogs` list surface;
- зафиксирован `my_dialogs` ownership handoff: после `reassign` старый owner
  теряет ticket из `my_dialogs`, новый owner получает его в `in_work`, а
  следующий client follow-up поднимает `unanswered` уже только у нового owner;
- дополнительно закреплено `resolve -> reopen` поведение для `my_dialogs`:
  `resolved` dialog исчезает из operator buckets, а после `reopen`
  возвращается в `in_work` без list-level drift.

## Проверка
- `spring-panel\.\mvnw.cmd "-Dtest=DialogDetailsIntegrationTest,DialogReadIntegrationTest,DialogWorkspaceIntegrationTest,NotificationApiIntegrationTest" test"`

## Дальше
- добрать соседние consumer contracts и remaining projection drift вокруг
  `queue/status-owner` surfaces после repeated follow-up refresh.
