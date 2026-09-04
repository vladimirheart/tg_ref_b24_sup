# Dialog reply target pulse and external message action rail

## Пользовательский запрос

> в диалогах, если был ответ на какое-то сообщение, кликнув на этот "ответ", история поднимается до сообщения, но пульсации сообщения, на которое был ответ, не видно.
> и ещё: троеточие меню сообщения вынеси за баббл сообщения

## Что изменено

- Исправлен источник стилей: reply pulse и portal menu теперь живут в source SCSS,
  а не только в generated app.css.
- Bubble и кнопка «⋯» переведены в общий flex-line, где action trigger является
  sibling справа от bubble.
- Добавлена заметная двухтактная theme-aware ring pulse исходного сообщения.
- Для prefers-reduced-motion используется статичное выделение.
- Восстановлен source-SCSS styling fixed portal меню сообщения.
- Для touch pointer trigger не скрывается полностью.
- Обновлены cache-busters dialogs runtime и app.css на странице диалогов.
- Generated CSS напрямую не редактируется.

## Проверки

- node --check dialogs-details-history-runtime.js.
- git diff --check.
- Docker Maven: DialogReplyTargetMessageActionsUiSourceContractTest.
- Isolated Docker Maven SCSS compilation в temp-каталоге.
- Проверка compiled temp app.css на новые source markers.
- SHA guard: tracked generated app.css/settings.css/sidebar.css/style.css не меняются.
