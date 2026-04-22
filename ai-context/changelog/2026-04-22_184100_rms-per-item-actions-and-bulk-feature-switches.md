# 2026-04-22 18:41:00 - RMS per-item actions and bulk feature switches

## Что изменено

- Для RMS-мониторов добавлены независимые флаги:
  `license_monitoring_enabled` и `network_monitoring_enabled`.
- Добавлены точечные backend-операции:
  обновление лицензии по одной записи и проверка доступности по одной записи.
- Добавлены массовые backend-операции:
  включение/отключение мониторинга лицензий и доступности сразу для всех RMS.
- Обновлены страница `/analytics/rms-control` и `rms-monitoring.js`:
  добавлены массовые кнопки управления, отдельные переключатели в формах
  и dropdown-действия по строке.
- Обновлены bootstrap и seed-импорт monitoring DB под новую схему RMS.

## Почему

- Пользователю нужна отдельная ручная проверка лицензии и доступности по каждой RMS.
- Пользователю нужно массово включать и отключать оба типа функционала независимо.
- Прежняя модель с одним общим `enabled` этого не позволяла.

## Затронутые файлы

- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/spring-panel/src/main/java/com/example/panel/entity/RmsLicenseMonitor.java`
- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/spring-panel/src/main/java/com/example/panel/repository/RmsLicenseMonitorRepository.java`
- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/spring-panel/src/main/java/com/example/panel/service/RmsLicenseMonitoringService.java`
- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/spring-panel/src/main/java/com/example/panel/controller/RmsLicenseMonitoringApiController.java`
- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/spring-panel/src/main/java/com/example/panel/service/MonitoringDatabaseBootstrapService.java`
- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/spring-panel/src/main/java/com/example/panel/service/RmsMonitoringSeedImportService.java`
- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/spring-panel/src/main/resources/templates/analytics/rms-control.html`
- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/spring-panel/src/main/resources/static/js/rms-monitoring.js`
- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/ai-context/tasks/task-list.md`
- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/ai-context/tasks/task-details/01-049.md`
- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/ai-context/changelog/2026-04-22_184100_rms-per-item-actions-and-bulk-feature-switches.md`

## Проверка

- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/spring-panel/mvnw.cmd -q -DskipTests compile`
