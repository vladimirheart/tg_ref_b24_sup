# Диагностика SQL PostgreSQL

Задача: `01-311`. Настройки действуют в production compose-контуре.

## Что собирается

- `pg_stat_statements`: агрегаты выполнения до 10 000 типов запросов,
  `track=top`, `track_planning=off`, `track_utility=off`.
- Запросы от `500ms` записываются в stderr PostgreSQL.
- Ожидания блокировок дольше `deadlock_timeout` записываются в журнал.
- Значения bind-параметров не журналируются. SQL-литералы в тексте запроса
  могут попасть в журнал; доступ к нему остаётся административным.
- Docker ограничивает stderr/stdout logs: по умолчанию три файла по 10 МБ.
  Этот журнал доступен через `docker logs`; автоматически в Loki он не отправляется.

Это инфраструктурное расширение. Оно не изменяет таблицы приложения или историю
Flyway. На новой базе его создаёт `docker/postgres/20-query-diagnostics.sql`.
На существующей базе этот же идемпотентный SQL выполняется явно после старта
PostgreSQL с preload; приложение и child-боты не устанавливают расширение.

## Применение к существующей базе

1. Прочитать фактические Compose project/config_files из labels контейнера.
2. Сохранить прежний command PostgreSQL и runtime image IDs как checkpoint.
3. Проверить `docker compose config` с тем же набором файлов.
4. Пересоздать только `postgres` с `up -d --no-deps --no-build postgres`.
   Требуется короткий перерыв: добавление preload требует restart БД.
5. Дождаться `healthy` и явно создать инфраструктурное расширение:

```powershell
docker exec tg_ref_b24_sup-postgres-1 psql -U iguana -d iguana -v ON_ERROR_STOP=1 -f /docker-entrypoint-initdb.d/20-query-diagnostics.sql
```

Имя контейнера, база и admin role в примере относятся к текущему локальному
контуру; для другой среды их необходимо получить из её фактического runtime.
Обычный restart существующего контейнера не перечитывает command Compose.

## Проверка

```sql
SELECT name, setting, pending_restart
FROM pg_settings
WHERE name IN ('shared_preload_libraries', 'pg_stat_statements.max',
  'pg_stat_statements.track', 'pg_stat_statements.track_planning',
  'pg_stat_statements.track_utility', 'log_min_duration_statement',
  'log_lock_waits', 'log_parameter_max_length');

SELECT extversion FROM pg_extension WHERE extname = 'pg_stat_statements';

SELECT queryid, calls, round(total_exec_time::numeric, 1) AS total_ms,
       round(mean_exec_time::numeric, 2) AS mean_ms,
       round(max_exec_time::numeric, 2) AS max_ms,
       shared_blks_hit, shared_blks_read, temp_blks_written
FROM pg_stat_statements
WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
ORDER BY total_exec_time DESC
LIMIT 20;
```

Проверить readiness панели, worker и runner после восстановления соединений.
Для единственного smoke slow-log допустим `SELECT pg_sleep(0.55)`; это
диагностическая задержка, а не нагрузочное тестирование рабочей БД.

## Накладные расходы и интерпретация

Модуль обновляет агрегаты на каждом учитываемом запросе. Нагрузка зависит от
числа SQL-операций, их длительности и конкуренции; универсального процента нет.
Planning и nested/utility tracking отключены, I/O timing дополнительно не включён.
Журнал пишет только медленные операции и lock waits.

Сравнивать режимы следует на отдельной БД с одинаковым workload, прогревом
и чередованием запусков. Короткий pgbench на тестовых таблицах оценивает только
этот workload и не является измерением изменения времени ответа рабочей панели.
Отдельно следить за active/pending Hikari и временем ожидания соединения:
SQL-агрегаты не включают ожидание соединения в Java.

## Изменение уровня диагностики и откат

- `APP_POSTGRES_STAT_STATEMENTS_TRACK=none` отключает сбор новых агрегатов.
- `APP_POSTGRES_LOG_MIN_DURATION_STATEMENT=-1` отключает slow-query log.
- `APP_POSTGRES_LOG_LOCK_WAITS=off` отключает журнал lock waits.
- Эти параметры переданы через command Compose; после изменения требуется
  пересоздать `postgres` тем же фактическим набором Compose-файлов.
- Полный откат preload: последним Compose override вернуть сохранённый прежний
  command. Для checkpoint этой задачи это `["postgres", "-c", "max_connections=550"]`.
  Пересоздать только PostgreSQL и проверить readiness зависимых сервисов.
- Удалять расширение для отключения сбора не требуется; данные приложения
  и Flyway остаются без изменений. Не очищать накопленную статистику без причины.
