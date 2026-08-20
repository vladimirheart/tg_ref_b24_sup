# Что сделал

- Обработал второй этап падения `spring-panel\run-windows.bat`: после устранения сетевой проблемы Maven приложение падало уже на старте Spring-контекста.
- Исправил внедрение зависимостей в `SlaEscalationAutoAssignService` и `ChannelAssignmentRoutingService`: у сервисов было по несколько конструкторов без явного `@Autowired`, из-за чего Spring пытался искать конструктор по умолчанию и завершал запуск с ошибкой.
- Согласовал локальный PostgreSQL bootstrap с runtime-настройками: для локального режима по умолчанию выставляются `APP_COORDINATION_MODE=direct`, `APP_COORDINATION_REQUIRED_FOR_POSTGRESQL=false` и `APP_STORAGE_OBJECT_REQUIRED_FOR_POSTGRESQL=false`, чтобы `run-windows.bat` не требовал Redis и S3 там, где bootstrap поднимает только PostgreSQL и RabbitMQ.
- Обновил генерацию `.env` в `scripts/bootstrap-first-run.ps1`, `scripts/bootstrap-first-run.sh` и шаблон `.env.example`, чтобы новые локальные значения появлялись сразу, а не только во время запуска батника.

# Зачем

- Пользовательский запрос: `падает сборка проекта`.
- Фактически сборка Maven уже проходила дальше, а реальная причина нового падения была в старте приложения: сначала в неверно выбранных конструкторах Spring-сервисов, затем в несовместимых локальных дефолтах для PostgreSQL bootstrap.

# Проверка

- Выполнил `spring-panel\mvnw.cmd -Dmaven.repo.local=.m2\repository -Dmaven.test.skip=true -q compile` — компиляция успешна.
- Выполнил `spring-panel\run-windows.bat` — процесс не завершился ошибкой и остался в рабочем состоянии, из-за чего консольный запуск упёрся только в таймаут ожидания команды.
- Проверил `curl.exe -I http://localhost:8080/login` — получен `HTTP/1.1 200`, значит приложение поднялось корректно.
- После проверки остановил локальный запуск через `spring-panel\stop-windows.bat --app-only`.
