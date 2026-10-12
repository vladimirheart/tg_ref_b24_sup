# 01-278 / S2A / R43 — точный ticket ID в watcher уведомлений

Время: 2026-10-12 16:15 MSK. Baseline: `fd4b263db9d8e36718a4b5d6751b992758d37235`.

Промт пользователя: «дальше».

Изменённые файлы:

- `spring-panel/src/main/java/com/example/panel/service/OperatorNotificationWatcher.java` — обе активные ветки SQL-чтения передают исходный непустой `ticket_id` в guard и downstream.
- `spring-panel/src/test/java/com/example/panel/service/OperatorNotificationWatcherTest.java` — PostgreSQL-регрессии для AI dispatch и overdue queue/audit/URL при plain/padded соседях; URL fixture следует реальному percent-encoding.
- `ai-context/tasks/task-details/01-278.md` — checkpoint R43.
- Этот append-only changelog-файл.

До fix: 7 watcher tests, 2 failures по ошибочной адресации plain sibling.
После fix: 31/31 GREEN в четырёх целевых классах, PostgreSQL Testcontainers.
Четыре generated CSS изменили только Windows EOL status, содержательного diff
нет и в пакет они не включены. Рабочая БД, migration/backfill, feature flags,
runtime и production не менялись. S2B/R12 не одобрены; статус 01-278 — 🟡.
