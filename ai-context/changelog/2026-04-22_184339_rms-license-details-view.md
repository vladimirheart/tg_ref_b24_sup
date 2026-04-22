# 2026-04-22 18:43:39 - RMS license details view

## Что изменено

- Для RMS-мониторов добавлено сохранение детализации состава лицензий в
  `license_details_json`.
- В `RmsLicenseMonitoringService` добавлен разбор списка лицензий из RMS XML
  и отдельный read-only метод получения сохранённого состава лицензий.
- В `RmsLicenseMonitoringApiController` добавлен endpoint
  `GET /api/monitoring/rms/sites/{id}/licenses`.
- На странице `/analytics/rms-control` добавлены:
  пункт `Посмотреть лицензии` в действиях строки и модальное окно быстрого
  просмотра лицензий.
- Bootstrap monitoring DB дополнен колонкой `license_details_json`.

## Почему

- Пользователю иногда нужно оперативно видеть не только общий статус лицензии,
  но и конкретный состав лицензий по отдельной RMS.
- Просмотр последних сохранённых данных быстрее и удобнее, чем каждый раз
  инициировать отдельную техническую проверку.

## Затронутые файлы

- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/spring-panel/src/main/java/com/example/panel/entity/RmsLicenseMonitor.java`
- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/spring-panel/src/main/java/com/example/panel/repository/RmsLicenseMonitorRepository.java`
- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/spring-panel/src/main/java/com/example/panel/service/MonitoringDatabaseBootstrapService.java`
- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/spring-panel/src/main/java/com/example/panel/service/RmsLicenseMonitoringService.java`
- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/spring-panel/src/main/java/com/example/panel/controller/RmsLicenseMonitoringApiController.java`
- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/spring-panel/src/main/resources/templates/analytics/rms-control.html`
- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/spring-panel/src/main/resources/static/js/rms-monitoring.js`
- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/ai-context/tasks/task-list.md`
- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/ai-context/tasks/task-details/01-050.md`
- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/ai-context/changelog/2026-04-22_184339_rms-license-details-view.md`

## Проверка

- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/spring-panel/mvnw.cmd -q -DskipTests compile`
