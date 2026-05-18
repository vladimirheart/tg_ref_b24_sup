# 2026-05-18 14:16:00 - passport sqlite compile fix

## Промпты пользователя

```text
tg_ref_b24_sup\spring-panel> .\run-windows.bat
...
[ERROR] /C:/Users/SinicinVV/git_h/tg_ref_b24_sup/spring-panel/src/main/java/com/example/panel/service/ObjectPassportService.java:[139,9] unreachable statement
[ERROR] /C:/Users/SinicinVV/git_h/tg_ref_b24_sup/spring-panel/src/main/java/com/example/panel/service/ObjectPassportService.java:[162,9] unreachable statement
```

## Что сделано

- В `ObjectPassportService` убраны недостижимые `throw` после перехода на `return fetchLastInsertRowId(connection)`.
- Сервис переписан в чистом виде без `getGeneratedKeys()` и без compile-time ошибки `unreachable statement`.
- Сохранена SQLite-совместимая логика получения id через `SELECT last_insert_rowid()`.

## Проверка

- `spring-panel\\mvnw.cmd -q -DskipTests compile`

## Итог

- `spring-panel` снова компилируется после SQLite-фикса паспорта объекта.
- `run-windows.bat` больше не должен падать на compile stage из-за `ObjectPassportService`.
