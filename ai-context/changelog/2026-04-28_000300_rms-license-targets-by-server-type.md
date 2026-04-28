# 2026-04-28 00:03:00 — Тип-зависимые целевые лицензии RMS

## Что изменено

- В RMS-мониторинге основной target license теперь выбирается по `server_type`:
  - `IIKO_CHAIN` → `FullEdition (Server)` с `id=10`
  - остальные RMS → `RMS (Front Fast Food)` с `id=100`
- Для `IIKO_RMS` добавлен отдельный расчёт количества лицензий `iikoConnector for Get Kiosk (iikoFront)` с `id=36073118`.
- В RMS DTO добавлены:
  - `target_license_id`
  - `target_license_label`
  - `kiosk_connector_license_count`
- Общий список RMS теперь показывает динамический label/id основной лицензии и, при наличии, kiosk-count.

## Затронутые файлы

- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-060.md`
- `spring-panel/src/main/java/com/example/panel/service/RmsLicenseMonitoringService.java`
- `spring-panel/src/main/java/com/example/panel/controller/RmsLicenseMonitoringApiController.java`
- `spring-panel/src/main/resources/static/js/rms-monitoring.js`

## Проверка

- `spring-panel\\mvnw.cmd -q -DskipTests compile`
