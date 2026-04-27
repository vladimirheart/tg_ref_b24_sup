# 2026-04-27 17:12:51 — RMS credentials encryption, modal create flow and availability overview

## Что изменено

- Введено encrypt-at-rest для `auth_password` RMS через `MonitoringCredentialsCryptoService` с AES-GCM и локальным ключом в `config/shared/monitoring-credentials.key`.
- `RmsLicenseMonitorRepository` переведён на прозрачное шифрование при записи и расшифровку при чтении.
- `MonitoringDatabaseBootstrapService` дополнен автоматической миграцией существующих plaintext-паролей RMS в зашифрованный формат.
- API RMS теперь отдаёт per-record состояния очередей обновления и aggregate availability overview.
- Страница `/analytics/rms-control` переведена на отдельную модалку добавления RMS, явный индикатор сохранённого пароля, статусы очереди по строкам и общий график доступности.
- В `ai-context` добавлены задача `01-057` и repo-specific правило по aggregate availability overview monitoring-страниц.

## Затронутые файлы

- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-057.md`
- `ai-context/rules/backend/03-monitoring-overview-availability.md`
- `spring-panel/src/main/java/com/example/panel/service/MonitoringCredentialsCryptoService.java`
- `spring-panel/src/main/java/com/example/panel/service/SharedConfigService.java`
- `spring-panel/src/main/java/com/example/panel/service/MonitoringDatabaseBootstrapService.java`
- `spring-panel/src/main/java/com/example/panel/repository/RmsLicenseMonitorRepository.java`
- `spring-panel/src/main/java/com/example/panel/service/RmsLicenseMonitoringService.java`
- `spring-panel/src/main/java/com/example/panel/controller/RmsLicenseMonitoringApiController.java`
- `spring-panel/src/main/resources/templates/analytics/rms-control.html`
- `spring-panel/src/main/resources/static/js/rms-monitoring.js`
- `spring-panel/monitoring.db`
- `config/shared/monitoring-credentials.key`

## Дополнительно

- Локально выполнена миграция текущих RMS-данных: plaintext-пароли в `monitoring.db` переведены в формат `enc:v1:*`.
- Проверка после миграции: `plaintext_count=0`.
- Сборка подтверждена командой `spring-panel\mvnw.cmd -q -DskipTests compile`.
