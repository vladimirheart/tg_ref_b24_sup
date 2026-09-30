# Правило: production storage boundaries Iguana

## Статус

Действует. Синхронизировано с PostgreSQL-only contract задачи `01-277`.

## Правило

Для production-архитектуры Iguana действуют следующие границы ownership:

- PostgreSQL является единственной SQL/реляционной БД проекта;
- `spring-panel` / Iguana backend владеет canonical business schema и Flyway migrations;
- `java-bot` и dynamic bot children получают PostgreSQL datasource contract, но не владеют schema bootstrap/migration;
- transport/runtime контуры ботов не должны становиться самостоятельным source of truth для диалогов, тикетов, задач, инцидентов и operator-facing истории;
- Redis отвечает за coordination, RabbitMQ — за transport, MinIO/S3 — за object storage; эти boundaries не заменяют PostgreSQL.

## Что это значит на практике

- единственный поддерживаемый SQL runtime mode — `postgresql`;
- launcher ботов передаёт canonical `APP_DB_MODE=postgresql` и `SPRING_DATASOURCE_*`;
- transport workers интегрируются через queue/API boundary и не выполняют schema ownership;
- новые runtime paths, tooling, tests и migrations не должны добавлять поддержку альтернативной SQL-БД или локального SQL fallback.

## Исторические записи

Changelog/task records могут содержать прежние DB contracts. Они сохраняются как история и не являются current guidance.

## Связанные артефакты

- `docs/database_distribution.md`
- `docs/BOT_RUNTIME_CONTRACT.md`
- `docs/environment_variables.md`
- `ai-context/tasks/task-details/01-277.md`
