# Dockerized production contour for Iguana

- Время: `2026-08-25 15:05:00`
- Файлы:
  - `docker-compose.production-contour.yml`
  - `docker/panel.Dockerfile`
  - `docker/bot.Dockerfile`
  - `.dockerignore`
  - `docs/docker-production-contour.md`
  - `.env.example`
  - `docs/environment_variables.md`
  - `README.md`
  - `spring-panel/src/main/java/com/example/panel/config/BotProcessProperties.java`
  - `spring-panel/src/main/java/com/example/panel/service/BotAutoStartService.java`
  - `spring-panel/src/test/java/com/example/panel/service/BotAutoStartServiceTest.java`
  - `ai-context/tasks/task-list.md`
  - `ai-context/tasks/task-details/01-208.md`
- Промты пользователя:

```text
проект вырос до работы с docker и postgres. нужно экспертно-аналитическое мнение, что ещё нужно и правильнее собирать именно через docker, например выделить микро-сервисы и их запуск осуществлять через docker

давай. оформи всё по правилам проекта и сразу приступай к выполнению

это одно из правил проекта - не забывай о нём. ты езё забыл создать задачу, что тоже есть в правилах проекта.

давай дальше по задаче
```

- Что сделано:
  - добавлен отдельный dockerized production-like contour с `spring-panel`, `PostgreSQL`, `RabbitMQ`, `Redis`, `MinIO` и внешними bot containers;
  - подготовлены Dockerfile для panel и bot runtime;
  - добавлен config guardrail `APP_BOT_AUTO_START_ENABLED`, чтобы panel не запускала child bot processes внутри containerized contour;
  - обновлены `README`, env contract и отдельная документация по docker contour;
  - заведена проектная задача `01-208` с detail-файлом для дальнейшего ведения этого infra-slice по правилам репозитория.
