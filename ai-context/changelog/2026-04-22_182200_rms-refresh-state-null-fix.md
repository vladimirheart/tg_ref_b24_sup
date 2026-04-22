# 2026-04-22 18:22:00 - RMS refresh state null fix

## Что изменено

- В `RmsLicenseMonitoringApiController` исправлена сборка `refresh_state` для RMS API.
- Вместо `Map.of(...)` с потенциальными `null` значениями использованы обычные
  `LinkedHashMap`.
- Добавлена задача `01-048` и её детализация в `ai-context/tasks`.

## Почему

- `Map.of(...)` не допускает `null`.
- При первом открытии страницы RMS поля `last_requested_at` и
  `last_completed_at` у очередей ещё пустые, что вызывало
  `500 Internal Server Error` в `GET /api/monitoring/rms/sites`.

## Затронутые файлы

- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/spring-panel/src/main/java/com/example/panel/controller/RmsLicenseMonitoringApiController.java`
- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/ai-context/tasks/task-list.md`
- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/ai-context/tasks/task-details/01-048.md`
- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/ai-context/changelog/2026-04-22_182200_rms-refresh-state-null-fix.md`

## Проверка

- `c:/Users/SinicinVV/git_h/tg_ref_b24_sup/spring-panel/mvnw.cmd -q -DskipTests compile`
