# 01-278: точный ticket ID при создании и поиске incident (R35)

Дата и время: 2026-10-12 12:16:44, Europe/Moscow.

Промт пользователя: `дальше`

## Изменение

- `IncidentService` сохраняет исходный nonblank ticket relation key при разборе
  `relations`, `ticket_id` и `ticket_ids`, валидации через legacy ticket lookup,
  записи relation и поиске incident по relation key. Padded/plain и literal
  ` null ` остаются разными ID. Ключи task/object_passport по-прежнему trim.
- UI создания incident сохраняет исходный ticket key до JSON request; для
  остальных relation types оставлена прежняя нормализация. Blank key отклоняется.

## Проверка и граница

- Baseline HEAD=origin/main `2a233b51f2a6ab1a2cccff63575f75e60f028381`,
  рабочий checkout был чистым; изменяемые blobs pinned. Проверка в detached
  worktree: baseline Java 3 tests, 1 error на trimmed ticket; Node RED на
  padded ID. После fix Java IncidentService + IncidentApiControllerWebMvcTest:
  6/6; Node R35 create-flow 6/6 и R34 transport 36/36; JS syntax GREEN.
  Sandbox exact-file scope и diff --check проверены; Sass CSS имел только
  Windows EOL status без content diff и в пакет не входит.
- Это source checkpoint S2A, не browser E2E, не production acceptance и не
  business isolation. Схема, рабочая БД, assignment, миграция/backfill,
  flags, runtime и external integrations не менялись. 01-278 остаётся 🟡,
  S2B/R12 PENDING/PROPOSED_NOT_APPROVED.
- Staging, commit, push и deployment агентом не выполнялись.

## Изменённые файлы

- `spring-panel/src/main/java/com/example/panel/service/IncidentService.java`
- `spring-panel/src/main/resources/static/js/incidents-workbench.js`
- `spring-panel/src/test/java/com/example/panel/service/IncidentServiceTest.java`
- `spring-panel/src/test/js/incidents-create-exact-ticket-id-r35.test.js`
- `ai-context/tasks/task-details/01-278.md`
- `ai-context/changelog/2026-10-12_121644_01-278_incident_relation_exact_ticket_identity.md`
