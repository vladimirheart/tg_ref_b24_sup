# 2026-11-25 — Диагностика SQL PostgreSQL, задача 01-311

## Промт пользователя

> ну давай попробуем - делай. но насколько я понимаю - это лишняя нагрузка на проект. вопрос насколько.

Запрос относится к включению `pg_stat_statements` и журнала медленных запросов.
Пользователь также попросил пояснить связь размера JDBC-пула с количеством
диалогов и передачу настроек дочерним ботам.

## Фактические изменения

- В `docker-compose.production-contour.yml` добавлены preload SQL-статистики,
  сбор top-level execution aggregates до 10 000 типов SQL, slow log от 500 мс,
  журнал lock waits и диагностический prefix. Planning/utility tracking выключены.
- Bind-параметры скрыты в обычных и error logs; существующая ротация 3 x 10 МБ
  сохранена.
- `.env.example` содержит параметры управления уровнем диагностики.
- Добавлен `docker/postgres/20-query-diagnostics.sql`: автоматическая установка
  расширения на новой базе и явное повторяемое подключение существующей базы.
- Добавлен runbook `docs/runbooks/postgresql-query-diagnostics.md` с применением,
  диагностическими SELECT, границами overhead и точным способом отката.
- Задача 01-311 зарегистрирована и завершена статусом `🟣`.

## Проверки и применение

- Изменения сначала проверены в detached worktree от
  `1d632fdc7945d92cd09df7bb00d3d4b4a465c90f`; Compose parser и diff check успешны.
- Изолированный PostgreSQL того же image подтвердил fresh init, наличие
  extension, сбор агрегатов и slow log. Синтетический pgbench: 6 прогонов по 8 с,
  4 клиента, 2 потока, 1 CPU, read-only extended protocol. Средняя latency:
  0.638 мс без session tracking/queryid/slow log и 0.659 мс с диагностикой (+3.3%).
  Это шумный короткий тест, а не оценка процента overhead рабочей панели.
- В рабочем контуре пересоздан только PostgreSQL с прежним image; установлен
  `pg_stat_statements` 1.10, настройки действуют без pending restart, агрегаты
  заполняются и smoke 556.741 мс появился в журнале.
- Readiness web/worker/runner = UP; их images, containers и StartedAt сохранены.
- max_connections=550 и Flyway V102 сохранены. Application migrations/backfill
  не выполнялись, внешние бизнес-системы не изменялись.
- Временный тестовый контейнер и его tmpfs-данные удалены после проверки ID.
- Staging, commit и push не выполнялись.
