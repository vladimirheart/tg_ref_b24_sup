# 2026-04-29 10:35:00 — RMS server_type normalization for license targets

## Что изменено

- В `RmsLicenseMonitoringService` добавлена нормализация legacy `server_type` значений `iikoChain` / `iikoOffice` в единые ключи `IIKO_CHAIN` / `IIKO_RMS`.
- В RMS API добавлены `server_type_key` и `server_type_display`, чтобы UI не зависел от сырого значения в БД.
- На странице RMS общий список теперь использует нормализованный тип для chain-подсветки, сортировки по типу и kiosk-бейджа.
- В `RmsMonitoringSeedImportService` новые seed-записи сразу получают нормализованный тип сервера.

## Затронутые файлы

- `spring-panel/src/main/java/com/example/panel/service/RmsLicenseMonitoringService.java`
- `spring-panel/src/main/java/com/example/panel/controller/RmsLicenseMonitoringApiController.java`
- `spring-panel/src/main/java/com/example/panel/service/RmsMonitoringSeedImportService.java`
- `spring-panel/src/main/resources/static/js/rms-monitoring.js`
- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-063.md`

## Зачем

Логика `01-060` не срабатывала на старых и seed-импортированных RMS-записях, потому что реальные значения `server_type` не совпадали с новыми кодовыми ключами. После нормализации один и тот же UI начинает корректно работать и на старых, и на новых данных.
