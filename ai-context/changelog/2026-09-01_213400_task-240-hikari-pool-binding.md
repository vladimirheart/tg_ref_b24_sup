# 2026-09-01 21:34:00 - Hikari pool binding

## Пользовательский запрос

> хорошо. дальше что по задаче?

## Изменения

- Compose передаёт лимит JDBC pool через фактически используемую настройку `APP_DB_MAX_POOL_SIZE`.
- В `.env` и `.env.example` добавлен `APP_POSTGRES_POOL_MAX_SIZE=6`.
- Справочник окружения дополнен параметром pool-limit.
