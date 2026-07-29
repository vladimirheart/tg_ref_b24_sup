# 2026-07-29 17:00:33 - max bot port recovery and template override cleanup

- Затронутые области:
  - `spring-panel/src/main/java/com/example/panel/service/BotProcessService.java`
  - `spring-panel/src/test/java/com/example/panel/service/BotProcessServiceTest.java`
  - `ai-context/tasks/task-details/01-152.md`
- Пользовательские промпты:
  - `не стартует бот мах, возвращая "Не удалось запустить бота: Бот не прошёл инициализацию: Web server failed to start. Port 18002 was already in use."`
  - `в списке ботов, у мах возвращает инфо: "... Вопросы: битый override ... id tpl_ml6opbj83rg3 не найден, используется шаблон по умолчанию ..."`
- Что сделано:
  - в `BotProcessService` добавлена pre-start recovery-проверка для `MAX`: перед запуском вычисляется зарезервированный порт канала, ищется локальный listener pid и при recognisable stale-process выполняется попытка штатной зачистки;
  - добавлены helper-методы для поиска pid слушающего процесса через `netstat` на Windows и `lsof/ss` на Unix-like окружениях;
  - recovery покрыт unit-тестами: отдельно проверен парсинг `netstat` и отдельно зачистка осиротевшего процесса через новый hook;
  - в runtime-данных `spring-panel/panel_runtime.db` очищен stale `question_template_id = tpl_ml6opbj83rg3` у `channelId=2`, потому что канонический `config/shared/settings.json` содержит только `tpl_mpnp8g3k1qcg` и `tpl_mpnr09amrzh9`.
- Проверки:
  - `./mvnw.cmd -q -DskipTests compile` в `spring-panel` — success
  - `./mvnw.cmd -q "-Dtest=BotProcessServiceTest" "-Dmaven.resources.skip=true" test` в `spring-panel` — success
- Операционные примечания:
  - локальный listener на `127.0.0.1:18002` (`PID 32496`) подтверждён как старый Spring Boot runtime `MAX`, но текущая Windows-сессия не дала завершить его через `Stop-Process`/`taskkill` (`Access is denied`), поэтому для полного live-unblock может понадобиться одноразовое завершение этого pid из той же или более привилегированной сессии, где он был поднят.
