# 2026-07-22 16:33:04 — flyway history heal and client phones migration

## Контекст
- Пользователь: `ловлю ошибку запуска:`
- `spring-panel\run-windows.bat` падал на Flyway при старте локальной `panel_runtime.db`.
- Помимо запоздалой legacy-миграции `V6_1__fix_client_phones_schema` в истории базы уже появились ложные строки `type='DELETE'` после предыдущего `repair()`, из-за чего Flyway пытался повторно проигрывать старые SQL-миграции.

## Что сделано
- В `spring-panel/src/main/java/db/migration/sqlite/V37__fix_client_phones_schema.java` добавлена новая хвостовая SQLite Java-миграция, которая идемпотентно приводит `client_phones` к нужной схеме.
- Удалён legacy-класс `spring-panel/src/main/java/db/migration/V6_1__fix_client_phones_schema.java`, чтобы старая версия `6.1` больше не участвовала в актуальной миграционной цепочке.
- В `spring-panel/src/main/java/com/example/panel/config/FlywayConfig.java` убран общий `flyway.repair()`; вместо него перед `migrate()` выполняется точечная нормализация `flyway_schema_history`:
  - удаление legacy-записи `6.1`, если она осталась в истории;
  - удаление избыточных `DELETE`-маркеров, когда в истории уже есть успешная исходная миграция с тем же `version/script`.
- При верификации локальный runtime подтвердил восстановление истории: лог зафиксировал удаление `23` ложных `DELETE`-записей, применение `V37` и успешный старт `PanelApplication`.

## Проверки
- `spring-panel\mvnw.cmd -Dmaven.repo.local=.m2\repository -Dmaven.test.skip=true clean compile`
- `spring-panel\run-windows.bat`

## Затронутые файлы
- `spring-panel/src/main/java/com/example/panel/config/FlywayConfig.java`
- `spring-panel/src/main/java/db/migration/sqlite/V37__fix_client_phones_schema.java`
- `spring-panel/src/main/java/db/migration/V6_1__fix_client_phones_schema.java`
- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-151.md`
