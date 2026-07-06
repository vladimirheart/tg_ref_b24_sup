# 2026-07-06 11:45:00 — Dialog my dialogs read and auto-processing buckets

## Пользовательский промпт

> 1. как только я прочитал новое сообщение из активного диалога, он переходит из статуса "неотвеченные" в статус "в работе".  
> это не логичное поведение
>
> 2. как только автоматически система забирает диалог в обработку - он пропадает из списка в окне диалога

## Затронутые файлы

- `spring-panel/src/main/java/com/example/panel/service/DialogLookupReadService.java`
- `spring-panel/src/main/resources/static/js/dialogs-my-dialogs-runtime.js`
- `spring-panel/src/main/resources/static/js/dialogs-list-runtime.js`
- `spring-panel/src/test/java/com/example/panel/service/DialogLookupReadServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/controller/DialogListIntegrationTest.java`
- `ai-context/changelog/2026-07-06_114500_dialog-my-dialogs-read-and-auto-processing-buckets.md`

## Что сделано

- В клиентской перегруппировке `Мои диалоги` убран special-case, который держал диалог в `Неотвеченных` только из-за `waiting_operator`, даже когда `unreadCount` уже стал `0` после чтения.
- На backend `auto_processing` без назначенного ответственного теперь считается видимым элементом секции `Новые`, а не выпадает из `my_dialogs` полностью.
- Во фронтовой синхронизации из строк таблицы и в табе `Новые` та же семантика закреплена отдельно, чтобы неназначенный `auto_processing` не исчезал из правого списка и не терялся между polling-циклами.
- Добавлены таргетные тесты на сервисную группировку и интеграционный list API сценарий для нового неназначенного `auto_processing` диалога.

## Проверки

- `node --check spring-panel/src/main/resources/static/js/dialogs-my-dialogs-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/dialogs-list-runtime.js`
- `spring-panel\\mvnw.cmd -q "-Dtest=DialogLookupReadServiceTest,DialogListIntegrationTest" test`
- `git diff --check -- spring-panel/src/main/java/com/example/panel/service/DialogLookupReadService.java spring-panel/src/main/resources/static/js/dialogs-my-dialogs-runtime.js spring-panel/src/main/resources/static/js/dialogs-list-runtime.js spring-panel/src/test/java/com/example/panel/service/DialogLookupReadServiceTest.java spring-panel/src/test/java/com/example/panel/controller/DialogListIntegrationTest.java ai-context/changelog/2026-07-06_114500_dialog-my-dialogs-read-and-auto-processing-buckets.md`
