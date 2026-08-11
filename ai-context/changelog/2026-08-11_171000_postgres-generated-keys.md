# 2026-08-11 17:10:00 - postgres generated keys cleanup

## Request
давай следующий шаг

## Summary
- убрал из active insert-path панели зависимость от SQLite-функции `last_insert_rowid()` и перевёл создание записей на JDBC generated keys;
- обновил репозитории monitoring-контура и сервисы reply/object-passport, чтобы новые `id` корректно получались и в external PostgreSQL-режиме;
- исправил PostgreSQL-миграцию `V8__client_blacklist_history.sql`, где оставался SQLite-синтаксис `AUTOINCREMENT` и текстовый `created_at`.

## Files Changed
- `spring-panel/src/main/java/com/example/panel/repository/SslCertificateMonitorRepository.java`
- `spring-panel/src/main/java/com/example/panel/repository/RmsRefreshQueueRepository.java`
- `spring-panel/src/main/java/com/example/panel/repository/RmsLicenseMonitorRepository.java`
- `spring-panel/src/main/java/com/example/panel/repository/IikoApiMonitorRepository.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogReplyTargetService.java`
- `spring-panel/src/main/java/com/example/panel/service/ObjectPassportService.java`
- `spring-panel/src/main/resources/db/migration/postgresql/V8__client_blacklist_history.sql`
