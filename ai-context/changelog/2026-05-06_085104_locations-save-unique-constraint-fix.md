# 2026-05-06 08:51:04 - locations save unique constraint fix

## Промпт пользователя

```text
при попытке сохранить изменения возвращает ошибку: ❌ Ошибка: PreparedStatementCallback; uncategorized SQLException for SQL [INSERT INTO settings_parameters(param_type, value, state, is_deleted, extra_json) VALUES (?, ?, 'Активен', 0, ?)]; SQL state [null]; error code [19]; [SQLITE_CONSTRAINT_UNIQUE] A UNIQUE constraint failed (UNIQUE constraint failed: settings_parameters.param_type, settings_parameters.value)
```

## Что сделано

- Переписан `SettingsParameterService` так, чтобы его логика соответствовала реальной схеме `settings_parameters` с уникальностью по `(param_type, value)`.
- Валидация ручного создания и обновления параметров теперь считает дублем любую активную запись с тем же `(param_type, value)`, независимо от зависимостей.
- `syncParametersFromLocationsPayload()` больше не пытается вставлять вторую запись при совпадающем `value`.
- Если запись уже существует, сервис не делает повторный `INSERT`, а при необходимости только мягко дополняет `extra_json` отсутствующими зависимостями.
- Интеграционные тесты переведены на схему с `UNIQUE(param_type, value)`, чтобы поведение соответствовало production SQLite.
- Добавлен тест на сценарий, где один и тот же город встречается в нескольких чейнах и не должен ломать сохранение.
- В `ai-context/tasks` добавлена и зафиксирована задача `01-073`.

## Проверка

- `spring-panel\mvnw.cmd -q "-Dnet.bytebuddy.experimental=true" "-Dtest=SettingsParameterServiceTest,SettingsParameterSharedConfigIntegrationTest,SettingsUpdateSharedConfigIntegrationTest,SettingsLocationsUpdateServiceTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Итог

- Сохранение `Структуры локаций` больше не должно падать на `SQLITE_CONSTRAINT_UNIQUE`, если одинаковый `city` или другое значение встречается в нескольких ветках дерева.
- Shared locations остаётся источником истины, а `settings_parameters` теперь синхронизируется в допустимом для схемы БД формате.
