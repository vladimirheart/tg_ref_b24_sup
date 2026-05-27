# 2026-05-27 14:50:02 — dialog list service regression net

## Что сделано
- `DialogLookupReadServiceTest` расширен service-level сценариями для
  `groupMyActiveDialogs(...)` вокруг `auto_processing + unread=0` и owner
  filtering после handoff;
- `DialogListReadServiceTest` дополнен проверкой `my_dialogs` payload contract
  для `unanswered/in_work` при `auto_processing` overlay и owner-aware
  list semantics;
- targeted пакет теперь прикрывает `queue/status-owner` drift не только на
  live `SpringBootTest`, но и на быстрых read-model/unit слоях.

## Проверка
- `spring-panel\.\mvnw.cmd "-Dtest=DialogLookupReadServiceTest,DialogListReadServiceTest,DialogListControllerWebMvcTest,DialogDetailsIntegrationTest,DialogReadIntegrationTest,DialogWorkspaceIntegrationTest,NotificationApiIntegrationTest" test"`

## Дальше
- добрать соседние consumer contracts и remaining projection drift вокруг
  `queue/status-owner` surfaces после repeated follow-up refresh.
