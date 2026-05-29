# 2026-05-28 18:17:33 — dialog list dedicated consumer boundary

## Что сделано
- добавлен новый `DialogListIntegrationTest` с live
  `SpringBootTest + SQLite` покрытием для `/api/dialogs`;
- отдельно закреплён null-auth contract: route возвращает `dialogs`, но
  держит `my_dialogs.unanswered/in_work` пустыми без operator identity;
- добран owner-aware lifecycle прямо на list surface: после `reassign`
  новый owner получает ticket в корректном bucket, а следующий follow-up
  поднимает `unanswered` уже на самом `/api/dialogs`;
- `DialogListControllerWebMvcTest` оставлен focused на transport delegation,
  без смешения с security/runtime semantics.

## Проверка
- `spring-panel\.\mvnw.cmd "-Dtest=DialogListControllerWebMvcTest,DialogListIntegrationTest,DialogLookupReadServiceTest,DialogListReadServiceTest" test"`

## Дальше
- добрать соседние consumer contracts и remaining projection drift вокруг
  `queue/status-owner` surfaces после repeated follow-up refresh.
