# Правило: production storage boundaries Iguana

## Статус

Действует.

## Правило

Для production-архитектуры Iguana действуют следующие границы ownership:

- `spring-panel` / Iguana backend является единственным владельцем business data;
- integration workers не должны владеть business schema и не должны выполнять её bootstrap/migration;
- transport/runtime контуры ботов не должны становиться самостоятельным source of truth для диалогов, тикетов, задач, инцидентов и operator-facing истории;
- `settings.db` и per-channel `bot-<channelId>.db` допустимы только как transitional/runtime artifacts, но не как target-state business storage.

## Что это значит на практике

- Внешний PostgreSQL-режим должен принадлежать backend-контуру, а не bot runtime.
- `java-bot` в external DB-режиме не должен включать schema ownership через `spring.sql.init`.
- Новые архитектурные задачи не должны добавлять business-таблицы в `bot_runtime.db`, `settings.db` или `bot-<channelId>.db` без отдельного пересмотра этого правила.
- Новые transport workers должны интегрироваться через queue/API boundary, а не через прямой JDBC к business DB.

## Transitional исключения

- SQLite-dev режим и текущие `APP_DB_*` compatibility aliases допустимы как этап миграции.
- Существующий split между `panel_runtime.db`, `panel_identity.db`, `monitoring.db`, `objects.db` и secondary SQLite-базами допускается только до завершения перехода по `docs/target-production-architecture-plan.md`.

## Связанные артефакты

- `docs/target-production-architecture-plan.md`
- `docs/database_distribution.md`
- `docs/db/sqlite-target-topology.md`
- `ai-context/tasks/task-details/01-181.md`
