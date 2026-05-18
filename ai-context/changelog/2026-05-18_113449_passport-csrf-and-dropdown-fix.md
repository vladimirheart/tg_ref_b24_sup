# 2026-05-18 11:34:49 - passport csrf and dropdown fix

## Пользовательский промпт

```text
при попытке сохранить возвращает в панели разработки в браузере: 
11:30:32.494 Uncaught TypeError: can't access property "classList", this._menu is null
    _isShown dropdown.js:245
    toggle dropdown.js:121
    <anonymous> dropdown.js:446
    n event-handler.js:118
18 dropdown.js:245:5
11:31:18.500 Error: Forbidden (CSRF or access denied)
    savePassport http://127.0.0.1:8080/object-passports/new:3249
new:3268:17
11:31:33.016 XHR
POST
http://127.0.0.1:8080/api/object_passports
[HTTP/1.1 403  1ms]

11:31:33.021 Error: Forbidden (CSRF or access denied)
    savePassport http://127.0.0.1:8080/object-passports/new:3249
    async* http://127.0.0.1:8080/object-passports/new:4575
new:3268:17
    savePassport http://127.0.0.1:8080/object-passports/new:3268
    <анонимный> http://127.0.0.1:8080/object-passports/new:4575
```

## Что сделано

- В `passports/new.html` добавлены:
  - `meta[name="_csrf"]`;
  - `meta[name="_csrf_header"]`;
  - подключение `/js/common.js`.
- Благодаря этому `fetch` на странице паспорта снова проходит через общий CSRF-wrapper проекта и отправляет `X-XSRF-TOKEN` для `POST/PUT`.
- В `registerSearchableSelect()` для кастомного режима (`data-force-custom-dropdown`) удаляются атрибуты `data-bs-toggle` и `data-bs-auto-close`, чтобы Bootstrap data-api не пытался открывать dropdown параллельно с ручной логикой страницы.
- В `ManagementControllerWebMvcTest` добавлена проверка, что редактор паспорта теперь действительно рендерит `common.js` и CSRF meta-тег.

## Проверка

- `spring-panel\mvnw.cmd -q "-Dnet.bytebuddy.experimental=true" "-Dtest=ManagementControllerWebMvcTest" test`
- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Итог

- Сохранение паспорта объекта больше не должно падать с `403 Forbidden (CSRF or access denied)` из-за отсутствующего CSRF на странице.
- Кастомные searchable-select на странице паспорта больше не должны ронять `dropdown.js` из-за параллельного запуска Bootstrap dropdown data-api.
