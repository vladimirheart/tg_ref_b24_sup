# 2026-05-18 13:24:00 - passport sqlite generated keys fix

## Промпты пользователя

```text
13:16:09.066 XHR
POST
http://127.0.0.1:8081/api/object_passports
[HTTP/1.1 500  113ms]

13:16:09.186 Error: Внутренняя ошибка сервера
    savePassport http://127.0.0.1:8081/object-passports/new:3257
    async* http://127.0.0.1:8081/object-passports/new:4586
    EventListener.handleEvent* http://127.0.0.1:8081/object-passports/new:4585
new
```

## Что сделано

- По `logs/spring-panel.log` найден точный backend-root-cause: `Caused by: java.sql.SQLFeatureNotSupportedException: not implemented by SQLite JDBC driver`.
- Выяснено, что `ObjectPassportService` использовал `Statement.RETURN_GENERATED_KEYS` и `PreparedStatement.getGeneratedKeys()` при создании `objects` и `object_passports`.
- В `ObjectPassportService` получение id после `INSERT` переведено на SQLite-совместимый `SELECT last_insert_rowid()` на том же соединении.
- Контракт API и frontend-логика сохранения не менялись: фикс ограничен слоем persistence.

## Проверка

- `spring-panel\\mvnw.cmd -q -DskipTests compile`

## Итог

- Причина `500` при создании нового паспорта объекта устранена на уровне SQLite persistence.
- После перезапуска `spring-panel` сохранение нового паспорта не должно падать на `getGeneratedKeys()`.
