# 2026-07-08 10:08:16 - locations tree refresh and visible notifications read

## Пользовательский промпт

> на странице настроек в структуре локаций не возвращает ни одной записи, хотя синхронизация с iiko проходит успешно. проверь что ничего не сломается при работе с вопросами клиента боту
>
> в уведомления счётчик прочитанных долджен пересчитываться по логике: открыл оповещения, и прочитанными становятся через 2 секунды те оповещение, которые видны в списке. и нужна принудительная кнопка "прочитать все"

## Затронутые файлы

- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-144.md`
- `spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js`
- `spring-panel/src/main/resources/static/js/settings-locations-iiko-runtime.js`
- `spring-panel/src/main/resources/static/js/sidebar.js`
- `spring-panel/src/main/resources/scss/sidebar/_notifications.scss`
- `spring-panel/src/main/resources/static/css/sidebar.css`

## Что сделано

- Для `locations` runtime добавлен принудительный page-data refresh поверх клиентского cache:
  - `settings-locations-tree-runtime.js` теперь умеет `forceReload` при построении дерева;
  - `settings-locations-iiko-runtime.js` при открытии модалки `locations` и после завершения sync заново подтягивает данные с backend и принудительно перестраивает дерево.
- Для bell dropdown изменена read-логика:
  - в `sidebar.js` добавлен `IntersectionObserver`-контур, который отслеживает только реально видимые unread-элементы;
  - запись становится read только если остаётся видимой 2 секунды;
  - после read локально пересчитывается badge и делается мягкий reload списка, чтобы unread/read секции не расходились с backend.
- Добавлено явное действие `Прочитать все`:
  - используется существующий `POST /api/notifications/read-all`;
  - после bulk-read badge и dropdown синхронно обновляются без изменения rearm semantics.
- Для dropdown добавлены стили toolbar/action-кнопки в SCSS и скомпилированный `sidebar.css`.
- В `ai-context/tasks` добавлена и зафиксирована задача `01-144`.

## Проверка

- `node --check spring-panel/src/main/resources/static/js/settings-locations-tree-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/settings-locations-iiko-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/sidebar.js`
- `java-bot\mvnw.cmd -q -pl bot-core -am "-Dtest=BotSettingsServiceTest" test`
- `spring-panel\mvnw.cmd --% -q -Dmaven.test.skip=true compile`

## Блокеры / примечания

- Полный `spring-panel` test run сейчас не проходит из-за уже существующего `testCompile` рассинхрона по media-reply сигнатурам в `Dialog*` тестах; это проявилось до выполнения целевых regression-тестов и не связано с текущим пакетом правок.
