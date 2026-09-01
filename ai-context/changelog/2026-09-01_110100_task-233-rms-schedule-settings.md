# 2026-09-01 11:01:00 - task 01-233 RMS schedule settings

## User prompt

> в аналитике есть страница контроля rms. нужна ручная настройка времени проверки доступности как ресторана, так и лицензий. сейчас стоит по умолчанию и без хард-кода изменить нельзя, что не правильно. то-же и с паузами между запросами

## Summary

- Added persisted RMS schedule policy in shared `settings.json` with current values as defaults: license checks every 1,440 minutes, availability checks every 5 minutes, and 20-second queue gap.
- Replaced fixed RMS check schedules with a worker dispatcher that applies saved intervals without a restart.
- Replaced the hard-coded queue pause with the persisted setting.
- Added RMS schedule GET/PATCH API and a settings modal on the RMS analytics page.
- Moved `01-233` to `🟣` for manual verification.

## Verification

- `spring-panel\\mvnw.cmd -DskipTests compile`: BUILD SUCCESS.
- `node --check spring-panel/src/main/resources/static/js/rms-monitoring.js`: passed.

## Files

- `spring-panel/src/main/java/com/example/panel/service/RmsMonitoringScheduleSettingsService.java`
- `spring-panel/src/main/java/com/example/panel/service/RmsLicenseMonitoringService.java`
- `spring-panel/src/main/java/com/example/panel/service/RmsLicenseMonitoringScheduler.java`
- `spring-panel/src/main/java/com/example/panel/controller/RmsLicenseMonitoringApiController.java`
- `spring-panel/src/main/resources/templates/analytics/rms-control.html`
- `spring-panel/src/main/resources/static/js/rms-monitoring.js`
- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-233.md`
