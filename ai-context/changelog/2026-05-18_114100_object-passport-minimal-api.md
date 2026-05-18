# 2026-05-18 11:41:00 - object passport minimal api

## Пользовательский промпт

```text
теперь ловлю такое поведение:
11:50:06.087 Error: Внутренняя ошибка сервера
    savePassport http://127.0.0.1:8080/object-passports/new:3257
    onSave http://127.0.0.1:8080/object-passports/new:4651
    handleSaveAction http://127.0.0.1:8080/js/edit-guard.js:256
    attemptClose http://127.0.0.1:8080/js/edit-guard.js:244
    handleToggle http://127.0.0.1:8080/js/edit-guard.js:230
    createToggleButton http://127.0.0.1:8080/js/edit-guard.js:219
    createToggleButton http://127.0.0.1:8080/js/edit-guard.js:219
    init http://127.0.0.1:8080/js/edit-guard.js:163
    registerBlock http://127.0.0.1:8080/js/edit-guard.js:470
    <anonymous> http://127.0.0.1:8080/object-passports/new:4650
    <anonymous> http://127.0.0.1:8080/object-passports/new:4647
```

## Что сделано

- По `logs/spring-panel.log` выявлена реальная причина: `POST /api/object_passports` не существовал в backend и уходил в `ResourceHttpRequestHandler` с ошибкой `No static resource api/object_passports`.
- Добавлен минимальный backend namespace `ObjectPassportApiController`:
  - `POST /api/object_passports`;
  - `PUT /api/object_passports/{id}`;
  - `GET /api/object_passports/{id}`;
  - `GET /api/object_passports/{id}/cases`;
  - `GET /api/object_passports/{id}/tasks`.
- Добавлен `ObjectPassportService`, который:
  - сохраняет payload редактора в `objects.db`;
  - использует JSON `details` в таблице `object_passports`;
  - создаёт/обновляет связанную запись в таблице `objects` для соблюдения `object_id` foreign key;
  - на update мерджит новый payload с уже сохранённым, чтобы не терять несабмитящиеся секции.
- В `passports/new.html` изменён `ensurePassportDataLoaded()`: существующий паспорт теперь догружается с API, если initial payload со страницы содержит только `id/is_new`.
- Добавлен новый WebMvc smoke test `ObjectPassportApiControllerWebMvcTest` на маршруты `create/get/update`.

## Проверка

- `spring-panel\mvnw.cmd -q -DskipTests compile`
- Попытка прогнать таргетные тесты через Maven упирается в уже существующие несвязанные ошибки глобального `src/test` дерева (`cannot find symbol` по множеству старых test-классов репозитория), поэтому стандартный `mvn test` сейчас не является надёжной проверкой именно этой доработки.

## Итог

- Базовое сохранение и загрузка паспорта объекта больше не зависят от отсутствующего API-контроллера.
- После рестарта `spring-panel` страница паспорта должна уметь:
  - создать новый паспорт;
  - открыть его по `id`;
  - повторно сохранить базовые поля без `500` на отсутствующем маршруте.
