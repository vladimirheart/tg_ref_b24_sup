# 2026-04-27 22:09:50 — RMS UI tooltips и снятие ложного отката в disabled

## Что изменено

- На странице RMS иконки `S/L/N` перенесены в первую колонку строки вместо текстового статуса сохранённого пароля.
- Из постоянного отображения строки RMS убраны:
  - дата последней проверки лицензии;
  - дата последнего ping.
- Время последних проверок сохранено в tooltip на статусных ячейках лицензии и RMS.
- В `RmsLicenseMonitoringService` убраны write-транзакции уровня сервиса для CRUD и массовых переключателей RMS, чтобы не падать на старте `monitoringTransactionManager` при кратковременном `SQLITE_BUSY` и дать отработать retry-логике репозитория.

## Что показал лог

- В `logs/spring-panel.log` зафиксирован runtime-сбой сохранения RMS:
  - `Unhandled exception on /api/monitoring/rms/sites/71`
  - `Could not open JDBC Connection for transaction`
  - первопричина: `org.sqlite.SQLiteException: [SQLITE_BUSY] The database file is locked`
- Это объясняет поведение, когда запись визуально редактируется, но после повторной загрузки возвращается к прежним disabled-флагам: часть `PATCH` не доходила до фактической записи в `monitoring.db`.

## Затронутые файлы

- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-059.md`
- `spring-panel/src/main/java/com/example/panel/service/RmsLicenseMonitoringService.java`
- `spring-panel/src/main/resources/static/js/rms-monitoring.js`

## Проверка

- `spring-panel\\mvnw.cmd -q -DskipTests compile`
