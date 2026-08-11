# 2026-08-11 18:10:00 - monitoring objects flyway bridge

## Request
давай следующий крупный шаг

## Summary
- добавлен PostgreSQL Flyway-срез `V12__monitoring_and_objects_bridge.sql`, который переносит в canonical external DB-path monitoring/object-таблицы, ранее остававшиеся только в SQLite bootstrap;
- расширен PostgreSQL schema coverage для `ssl_certificate_monitors`, `rms_refresh_queue`, `iiko_api_monitors`, `monitoring_check_history`, `objects` и `object_passports`;
- синхронизированы missing columns для `rms_license_monitors` (`license_monitoring_enabled`, `license_details_json`, `license_debug_excerpt`, `network_monitoring_enabled`, `is_deleted`, `deleted_at`) через PostgreSQL Flyway, а не через runtime bootstrap.

## Files Changed
- `spring-panel/src/main/resources/db/migration/postgresql/V12__monitoring_and_objects_bridge.sql`
