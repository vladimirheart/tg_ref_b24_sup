# 2026-07-31 17:28:23 - flyway baseline checksum repair follow-up

- Затронутые области:
  - `spring-panel/src/main/java/com/example/panel/config/FlywayConfig.java`
  - `spring-panel/panel_runtime.db`
  - `ai-context/tasks/task-list.md`
  - `ai-context/tasks/task-details/01-163.md`
- Пользовательский промпт:
  - `spring-panel> .\\run-windows.bat [INFO] Java runtime: 25.0.2 (major 25) [WARN] ...`
  - `The attached pasted text file(s) contain the user's request. Read and act on that content.`
- Что сделано:
  - в `FlywayConfig` убран fallback на устаревший checksum baseline `V1` и оставлена безопасная ветка с явным логированием, если локальный checksum заранее не удаётся получить;
  - startup recovery для mutable baseline переведён на безусловный `flyway.repair()` при наличии успешной записи `V1__baseline_schema.sql` в schema history;
  - реальный runtime `panel_runtime.db` выровнен через repair до checksum `1965522692`, после чего `spring-panel` снова стартует без `FlywayValidateException`;
  - задача `01-163` переведена в статус `🟣` и дополнена результатами проверки.
- Проверки:
  - `spring-panel\\mvnw.cmd -q -DskipTests compile` — success
  - `spring-panel\\run-windows.bat` — приложение доходит до `Started PanelApplication`
  - `sqlite3 spring-panel\\panel_runtime.db "select installed_rank, version, script, checksum, success from flyway_schema_history where version='1' order by installed_rank;"` — обе успешные записи `V1` имеют checksum `1965522692`
