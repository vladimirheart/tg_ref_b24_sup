# 2026-08-17 17:49:00 - local runtime bootstrap and panel url clarity

## Промпты пользователя

- `докер говорит:
Docker Desktop failed to start because virtualisation support wasn’t detected. Contact your IT admin to enable virtualization or check system requirements.
и не могу открыть страницу проекта после ввода авторизации`

## Что изменено

- в `spring-panel/src/main/java/com/example/panel/service/DatabaseBootstrapService.java` добавлен bootstrap общей локальной `bot_runtime.db` схемы для SQLite dev-mode:
  - создаются таблицы `feedbacks` и `client_unblock_requests`;
  - создаётся индекс `idx_client_unblock_requests_user`;
  - это закрывает runtime-падение панели после авторизации на fresh/local SQLite контуре, где shared bot-runtime БД ещё не была инициализирована;
- в `spring-panel/run-windows.bat` добавлен явный вывод итогового URL панели:
  - после выбора свободного порта скрипт теперь печатает `Panel URL: http://localhost:<port>/`;
  - это убирает путаницу между старым занятым `8080` и новым фактическим портом, например `8082`.

## Проверка

- по `logs/spring-panel.log` свежий `spring-panel` после фикса стартует без ошибки `no such table: client_unblock_requests`;
- `curl.exe -I http://localhost:8082/login` возвращает `HTTP 200`;
- `netstat -ano -p tcp` подтверждает, что старый процесс продолжал слушать `8080`, а свежий инстанс панели поднялся на `8082`.
