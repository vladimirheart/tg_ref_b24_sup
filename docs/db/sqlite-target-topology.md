# Историческая SQLite topology — superseded

> **Статус: исторический документ.** Изначальная target-модель нескольких SQLite databases больше не является целевой production архитектурой. После PostgreSQL cutover `01-228` и cleanup `01-229` canonical business/runtime storage — PostgreSQL.

Этот путь сохранён как navigation anchor для старых changelog/task ссылок. Использовать его как инструкцию по проектированию нового runtime нельзя.

## Что актуально сейчас

- `spring-panel` production runtime использует PostgreSQL; `APP_DB_MODE=sqlite` отклоняется.
- users/monitoring/business/object reads и writes не открывают отдельные SQLite datasources.
- legacy `panel_runtime.db`, `panel_identity.db`, `monitoring.db`, `bot_runtime.db`, `clients.db`, `knowledge_base.db`, `objects.db` и per-channel shards являются только archive/import/recovery evidence или test fixtures.
- isolated bot worker technical SQLite допустим только для self-owned technical state и не может быть canonical business source of truth.

## Текущие источники истины по storage

- [../database_distribution.md](../database_distribution.md) — production ownership;
- [../database-paths.md](../database-paths.md) — legacy archive/import source hints;
- [../SQLITE_BOOTSTRAP_PERIMETER.md](../SQLITE_BOOTSTRAP_PERIMETER.md) — разрешённый residual SQLite perimeter;
- [../../ai-context/rules/backend/04-sqlite-topology.md](../../ai-context/rules/backend/04-sqlite-topology.md) — project rule.

Исторические решения о “5 SQLite contours” следует читать только в соответствующих старых task/changelog snapshots, а не как current target-state.
