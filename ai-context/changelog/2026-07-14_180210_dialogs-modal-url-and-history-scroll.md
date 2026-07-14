# Изменение: lifecycle URL модалки диалога и стабильный scroll-to-bottom истории

## Промпт пользователя

Пользователь приложил контекст с двумя UX-задачами на странице диалогов:

1. Исправить lifecycle URL модалки диалога:
   - при открытии модалки сохранять исходный URL списка диалогов вместе с `pathname`, `search` и `hash`;
   - при закрытии модалки возвращать именно исходный URL списка, а не оставлять `/dialogs/{ticketId}`;
   - не ломать прямое открытие `/dialogs/{ticketId}`, back/forward браузера, workspace mode и `initialDialogTicketId`.

2. Исправить scroll-to-bottom истории:
   - при открытии модалки история должна доходить до самого низа;
   - после отправки сообщения история тоже должна оставаться внизу;
   - после догрузки `img` / `video` / `audio` / sticker preview высота истории должна пересчитываться без ручного рывка пользователя вверх.

Также требовалось по возможности использовать существующую инфраструктуру тестов, а при её отсутствии хотя бы сделать безопасную техническую проверку runtime-файлов.

## Что сделано

- В `spring-panel/src/main/resources/static/js/dialogs-flow-runtime.js` добавлен полноценный modal URL lifecycle:
  - сохранение `returnUrl` до `pushState`;
  - `history.pushState({ surface, ticketId, returnUrl })` при открытии;
  - восстановление исходного URL списка через `replaceState` при закрытии модалки;
  - отдельная обработка `popstate` для сценариев Back/Forward и прямого перехода на `/dialogs/{ticketId}`.

- В `spring-panel/src/main/resources/static/js/dialogs-details-history-runtime.js` вынесен общий helper прокрутки истории:
  - `scrollHistoryToBottom()` с повторными проходами через `requestAnimationFrame` и задержки;
  - `isHistoryPinnedToBottom()` и хранение намерения пользователя оставаться внизу;
  - повторный доскролл после гидратации медиа и загрузки `img/video/audio`;
  - сохранение viewport при подгрузке старой истории без насильственного сброса вниз.

- В `spring-panel/src/main/resources/static/js/dialogs.js` прокинуты новые helper’ы между runtime-модулями:
  - `buildDialogsListUrl`;
  - accessors для активного тикета/канала;
  - route helpers для `channelId`;
  - вызов `bindModalNavigationEvents()` при инициализации dialogs runtime.

## Проверки

- `node --check spring-panel/src/main/resources/static/js/dialogs-flow-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/dialogs-details-history-runtime.js`
- `node --check spring-panel/src/main/resources/static/js/dialogs.js`

Отдельной JS test-инфраструктуры (`vitest` / `jest` / runtime spec files) в репозитории не найдено, поэтому выполнена синтаксическая проверка изменённых runtime-файлов.
