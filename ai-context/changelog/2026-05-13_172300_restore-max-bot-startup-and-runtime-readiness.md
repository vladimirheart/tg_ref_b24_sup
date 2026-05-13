# 2026-05-13 17:23:00 — restore max bot startup and runtime readiness

## Промпты пользователя

- `теперь не хотят запускаться боты, ни мах ни telegram`
- `продолжи`

## Что изменено

- В `spring-panel/src/main/java/com/example/panel/service/BotRuntimeContractService.java` добавлена прямая передача bot runtime-параметров:
  - `SUPPORT_BOT_DATABASE_PATH` с путём к общей SQLite-базе панели;
  - `SPRING_SQL_INIT_MODE=always` для гарантированной инициализации схемы bot-core;
  - сохранён существующий `APP_DB_TICKETS` для совместимости.
- В `spring-panel/src/main/java/com/example/panel/service/BotProcessService.java` добавлено короткое окно стабилизации после `Started ...`, чтобы процесс считался готовым только если пережил startup marker и не упал сразу после него.
- В `spring-panel/src/test/java/com/example/panel/service/BotRuntimeContractServiceTest.java` обновлены проверки runtime env на новые прямые параметры.
- В `spring-panel/src/test/java/com/example/panel/service/BotProcessServiceTest.java` добавлен сценарий, где процесс печатает `Started ...` и сразу завершается; панель теперь корректно считает это ошибкой старта.

## Проверка

- Выполнены целевые тесты:
  - `.\mvnw.cmd "-Dnet.bytebuddy.experimental=true" "-Dtest=BotRuntimeContractServiceTest,BotProcessServiceTest" test`
- Выполнена сборка:
  - `.\mvnw.cmd -DskipTests package`
- Выполнен живой прогон через `spring-panel`:
  - `MAX`-бот автозапустился и остался живым;
  - в `logs/support-bot-max-2-process.log` подтверждён успешный startup, создание `trg_on_ticket_resolved` и переход в `ReadinessState ACCEPTING_TRAFFIC`;
  - в `logs/spring-panel.log` подтверждены строки `Started bot process for channel 2` и `Auto-started bot for channel 2`.

## Остаточный риск

- `Telegram`-бот по-прежнему не запускается, потому что у канала настроен `mtproto`, а текущий bot runtime поддерживает только `HTTP`, `HTTPS`, `SOCKS4`, `SOCKS5` и `VLESS`.
