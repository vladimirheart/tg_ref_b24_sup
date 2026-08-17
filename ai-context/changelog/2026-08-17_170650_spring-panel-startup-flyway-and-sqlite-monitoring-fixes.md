# 2026-08-17 17:06:50 - spring panel startup flyway and sqlite monitoring fixes

## Пользовательский промпт

`\spring-panel> powershell -NoLogo -NoProfile -ExecutionPolicy Bypass -File .\sc…`

## Что изменено

- в `spring-panel` устранён Flyway-конфликт версий для SQLite-цепочки:
  - Java-миграция `client_phones` переименована из `V37__...` в `V37_1__...`;
  - `FlywayConfig` теперь перед `migrate()` автоматически переносит уже применённую legacy-запись `db.migration.sqlite.V37__fix_client_phones_schema` с версии `37` на `37.1`, чтобы не конфликтовать с SQL-миграцией `V37__integration_inbound_event_inbox.sql`;
- для monitoring SQLite-репозиториев добавлен общий helper `JdbcGeneratedKeySupport`;
- `SslCertificateMonitorRepository`, `RmsLicenseMonitorRepository`, `IikoApiMonitorRepository` и `RmsRefreshQueueRepository` больше не зависят от `getGeneratedKeys()`, который не реализован в текущем SQLite JDBC driver:
  - insert-path теперь сначала пробует обычный generated key;
  - для SQLite fallback-ом использует `SELECT last_insert_rowid()` в рамках того же connection.

## Проверка

- `cmd /c run-windows.bat` из `spring-panel` больше не падает на `Found more than one migration with version 37`;
- после фикса `run-windows.bat` дошёл до рабочего старта, а `http://localhost:8080/login` отвечает `HTTP 200`.
