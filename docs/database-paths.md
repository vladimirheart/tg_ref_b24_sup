# Пути к БД и переменные окружения

## Python (panel/app.py)
- Все пути читаются из `config/settings.py` через `get_settings()`.
- По умолчанию SQLite-файлы лежат в корне репозитория:
  - `tickets.db` — основная БД (тикеты, задачи и т.п.).
  - `users.db` — учетные записи для веб-панели.
  - `bot_database.db` — технические данные бота.
  - `object_passports.db` — паспорта объектов.
- Переопределяются переменными окружения:
  - `APP_DB_TICKETS`, `APP_DB_USERS`, `APP_DB_BOT`, `APP_DB_OBJECT_PASSPORTS`.
- В `panel/app.py` пути материализуются в константы `TICKETS_DB_PATH`, `USERS_DB_PATH`, `OBJECT_PASSPORT_DB_PATH` и используются во всех хендлерах.

## Python (bot_support.py)
- Использует тот же `get_settings()` и открывает соединения через `DB_PATH = settings.db.tickets_path` — это та же `tickets.db`, что и у веб-панели.
- `ensure_schema_is_current()` и `ensure_channel_tables()` применяют миграции к этому файлу при старте, поэтому важно запускать бота с тем же путем, что у панели.

## Java Spring Panel
- В `spring-panel/src/main/resources/application.yml` путь к SQLite берётся из `app.datasource.sqlite.path` (по умолчанию `../tickets.db`).
- Чтобы использовать те же БД, что и Python-приложения, перед стартом Java-панели задайте такие же абсолютные пути:
  ```shell
  set APP_DB_TICKETS=путь до проекта через 2 слэша\\tickets.db
  set APP_DB_USERS=путь до проекта через 2 слэша\\users.db
  set APP_DB_BOT=путь до проекта через 2 слэша\\bot_database.db
  set APP_DB_OBJECT_PASSPORTS=путь до проекта через 2 слэша\\object_passports.db
  ```
- Java-версия ищет только `APP_DB_TICKETS`, но использование одинакового набора переменных гарантирует совпадение путей и упрощает отладку.

## Почему сайдбар пустой, если нет БД
- Ссылки в боковом меню рендерятся только при наличии соответствующих прав (`PAGE_*`), которые читаются из таблиц `users` и `user_authorities` SQLite-базы.
- Если файл `tickets.db` не найден или таблицы пусты, авторизации нет → все пункты меню скрыты, даже если HTML и JS загрузились без ошибок.
- Убедитесь, что Java-панель видит ту же `tickets.db`, либо дайте ей создать дефолтного администратора на пустой БД.
