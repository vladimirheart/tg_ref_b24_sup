# 2026-05-13 22:25:05 - rms soft delete and seed hide

## Промпты пользователя

```text
на странице контроля рмс rms-control удаляю не нужные записи, но это срабатывает лишь до перезапуска проекта, удалённые записи всё равно продолжают отображаться. да, полное удаление неприемлимо, но из списка они должны пропадать
```

## Что изменено

- В `spring-panel/src/main/java/com/example/panel/entity/RmsLicenseMonitor.java` для RMS-мониторов добавлены поля soft-delete:
  - `is_deleted`
  - `deleted_at`
- В `spring-panel/src/main/java/com/example/panel/repository/RmsLicenseMonitorRepository.java`:
  - обычные методы выборки теперь возвращают только не скрытые записи;
  - добавлены методы для работы со всеми записями, включая скрытые;
  - `DELETE` заменён на `softDeleteById(...)`;
  - `INSERT/UPDATE` расширены новыми полями soft-delete.
- В `spring-panel/src/main/java/com/example/panel/service/MonitoringDatabaseBootstrapService.java` добавлена bootstrap-миграция схемы `monitoring.db`:
  - создаются/добавляются колонки `is_deleted` и `deleted_at`;
  - миграция RMS из primary DB теперь учитывает и скрытые записи, чтобы не упираться в unique по адресу.
- В `spring-panel/src/main/java/com/example/panel/service/RmsMonitoringSeedImportService.java` seed-импорт теперь считает скрытые RMS уже существующими, поэтому удалённые seed-адреса не возвращаются после рестарта.
- В `spring-panel/src/main/java/com/example/panel/service/RmsLicenseMonitoringService.java`:
  - удаление RMS переведено на soft-delete;
  - повторное добавление ранее скрытой RMS восстанавливает старую запись по `rms_address`;
  - обновление RMS снимает soft-delete, если запись возвращена в рабочее состояние.
- В `spring-panel/src/main/resources/static/js/rms-monitoring.js` тексты удаления уточнены под новое поведение: запись убирается из списка, но остаётся в базе и может быть возвращена повторным добавлением.
- Добавлен тест `spring-panel/src/test/java/com/example/panel/service/RmsLicenseMonitoringServiceTest.java` на два ключевых сценария:
  - soft-delete вместо физического удаления;
  - восстановление ранее скрытой RMS при повторном добавлении.
- В `ai-context/tasks/task-list.md` добавлена запись о задаче `01-085`.

## Проверка

- Выполнен целевой тест:
  - `.\mvnw.cmd "-Dtest=RmsLicenseMonitoringServiceTest" test`
- Выполнена компиляция:
  - `.\mvnw.cmd "-DskipTests" "-Dmaven.compiler.useIncrementalCompilation=false" compile`
- `git diff --check` по-прежнему ругается на уже существующие trailing whitespace в `logs/spring-panel.log`; новые изменения этого не добавляли.

## Итог

- Удалённые RMS теперь скрываются из списка без физического удаления из базы.
- Seed-импорт больше не возвращает скрытые RMS обратно после рестарта.
- Повторное добавление того же адреса восстанавливает старую запись вместо конфликта по unique-индексу.
