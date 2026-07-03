# 2026-07-03 11:18:15 — Dialogs my dialogs right sidebar and new unassigned section

## Связанные задачи

- `01-139` — Перенести список «Мои диалоги» вправо и показывать новые неназначенные обращения в открытом диалоге

## Пользовательский промпт

> приступай к выполнению задачи 01-139

## Затронутые файлы

- `ai-context/tasks/task-list.md`
- `spring-panel/src/main/java/com/example/panel/controller/DialogsController.java`
- `spring-panel/src/main/java/com/example/panel/model/dialog/DialogMyDialogs.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogListReadService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogLookupReadService.java`
- `spring-panel/src/main/resources/scss/app/_dialogs.scss`
- `spring-panel/src/main/resources/static/css/app.css`
- `spring-panel/src/main/resources/static/js/dialogs-my-dialogs-runtime.js`
- `spring-panel/src/main/resources/static/js/dialogs-shell-runtime.js`
- `spring-panel/src/main/resources/static/js/dialogs.js`
- `spring-panel/src/main/resources/templates/dialogs/index.html`
- `spring-panel/src/test/java/com/example/panel/controller/DialogsControllerWebMvcTest.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogListReadServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogLookupReadServiceTest.java`

## Что сделано

- На сервере расширен контракт `DialogMyDialogs`: добавлен отдельный бакет `newUnassigned` для новых неназначенных обращений, который формируется вместе с текущими группами `unanswered` и `inWork`.
- На странице диалога и в polling payload протянут новый список `myNewDialogs` / `my_dialogs.new`, чтобы модалка видела новые обращения без возврата к общему списку.
- В legacy-модалке список `Мои диалоги` дополнен верхней секцией `Новые`; пустое состояние и общий счётчик теперь учитывают как новые неназначенные, так и назначенные оператору диалоги.
- Sidebar модалки переставлен на правую сторону окна, а логика ресайза обновлена под новый край ручки, чтобы ширина панели продолжала корректно меняться.
- Обновлены WebMvc/service-тесты под новый серверный контракт; в `DialogListReadServiceTest` заодно убрано старое мокирование final-типа `DialogSummary`, из-за которого таргетный прогон тестов падал.
- Пересобран `static/css/app.css` из SCSS и поднята asset-version страницы dialogs для сброса клиентского кэша.

## Проверки

- `spring-panel\\mvnw.cmd -q generate-resources`
  - результат: SCSS успешно пересобран, `static/css/app.css` обновлён.
- `node --check C:/Users/sushi/Git_H/tg_ref_b24_sup/spring-panel/src/main/resources/static/js/dialogs-my-dialogs-runtime.js`
  - результат: синтаксис корректен.
- `node --check C:/Users/sushi/Git_H/tg_ref_b24_sup/spring-panel/src/main/resources/static/js/dialogs-shell-runtime.js`
  - результат: синтаксис корректен.
- `node --check C:/Users/sushi/Git_H/tg_ref_b24_sup/spring-panel/src/main/resources/static/js/dialogs.js`
  - результат: синтаксис корректен.
- `spring-panel\\mvnw.cmd -q "-Dtest=DialogsControllerWebMvcTest,DialogListReadServiceTest,DialogLookupReadServiceTest" test`
  - результат: таргетные тесты проходят.
- `git diff --check`
  - результат: ошибок diff-formatting нет; Git выводит только стандартные предупреждения LF/CRLF для Windows-рабочей копии.
