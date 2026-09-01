# 2026-09-01 21:21:00 - PostgreSQL connection capacity

## Пользовательский запрос

> PostgreSQL исчерпал подключения (`too many clients already`) что делать? как увеличить?

## Изменения

- PostgreSQL Docker service запускается с настраиваемым `max_connections`.
- В `.env` и `.env.example` добавлен `APP_POSTGRES_MAX_CONNECTIONS=150`.
- В справочник переменных окружения добавлено назначение и правило выбора лимита.
