# 01-277 — PostgreSQL-only migration chains cleanup, S3

## Инициирующее сообщение

> Работа по проекту. Прочти CHAT_HANDOFF_20260929_01-277_POSTGRES_ONLY_S2_GREEN_S3_NEXT.md и продолжай с текущего состояния. Следующий шаг — S3: удалить MySQL/SQLite migration chains и Java SQLite migrations, сохранив единственный PostgreSQL chain.

## Изменение

- удалены spring-panel/src/main/resources/db/migration/mysql/**;
- удалены spring-panel/src/main/resources/db/migration/sqlite/**;
- удалены spring-panel/src/main/java/db/migration/sqlite/**;
- удалены dedicated SQLite migration tests;
- source-contract tests миграций переведены на PostgreSQL-only contract;
- из FlywayConfig удалён SQLite-specific legacy history remap;
- PostgreSQL migration chain сохранён без функциональных изменений.

## Safety

S3 является source-only cleanup: не запускает Flyway against production, не меняет PostgreSQL data, runtime containers, queues, environment или images. Общие SQLite/H2 test fixtures не входят в этот slice и остаются до S4.
