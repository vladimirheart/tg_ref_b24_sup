# 2026-07-02 15:58:50 — Dialog history modal stability and bubble actions

## Связанные задачи

- `01-138` — Стабилизировать history-модалку диалога и убрать шум из карточек сообщений

## Пользовательский промпт

> в модалке диалога нужно внести правки:  
> 1. постоянно прыгает и обновляется визуально состав диалога, и смещается примерно на предпоследнее сообщение - это ужасное поведение для оператора.  
> 2. в карточке сообщения указывается "[voice]" или "[video_note]", плюс есть кнопка "скачать" и "файл". это нужные вещи, но убери эту инфо в всплывающее инфо, т.к. в бабле сообщения очень много шума  
> 3. всплывающее меню у бабла сообщения сейчас появляется при наведении курсора - это ок, но область должна быть в рамках троеточия, как и было запланированно

## Затронутые файлы

- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-138.md`
- `spring-panel/src/main/resources/scss/app/_core.scss`
- `spring-panel/src/main/resources/scss/app/_dialogs.scss`
- `spring-panel/src/main/resources/static/css/app.css`
- `spring-panel/src/main/resources/static/js/dialogs-details-history-runtime.js`

## Что сделано

- В `dialogs-details-history-runtime.js` исправлено сравнение history marker: теперь poll сравнивает только отфильтрованные операторские сообщения, поэтому скрытые technical/feedback элементы больше не провоцируют ложную перерисовку модалки на каждом цикле.
- В history runtime добавлено сохранение viewport при реальном обновлении истории: если оператор не находится внизу ленты, модалка больше не уводит его вниз или к предпоследнему сообщению; если оператор уже внизу, поведение sticky-bottom сохраняется.
- Для media-only сообщений убран fallback-текст вида `[voice]` / `[video_note]`, а также вынесены `Скачать` и имя файла из тела бабла в компактное всплывающее info у вложения.
- Для reply-целей добавлен отдельный `data-message-preview`, чтобы reply по media-only сообщению показывал осмысленный preview даже без текстового body.
- В SCSS/CSS поправлена геометрия action-menu сообщения: кнопка троеточия показывается на hover bubble, но hover/open-зона меню ограничена самим троеточием и выпадающим списком, а не широкой невидимой областью вокруг сообщения.

## Проверки

- `.\mvnw.cmd -q generate-resources`
  - результат: SCSS успешно пересобран в `static/css/app.css`.
- `.\mvnw.cmd -q -DskipTests compile`
  - результат: проект компилируется.
- `node --check spring-panel/src/main/resources/static/js/dialogs-details-history-runtime.js`
  - результат: синтаксис JS корректен.
- `git diff --check`
  - результат: ошибок diff-formatting нет; Git показал только стандартные предупреждения LF/CRLF для Windows-рабочей копии.
- `.\mvnw.cmd -q -Dtest=DialogsControllerWebMvcTest test`
  - результат: не выполнен из-за уже существующих ошибок `testCompile` в unrelated тестах `DialogQuickActions*`, `DialogApiControllerWebMvcTest`, `DialogReplyServiceTest`, связанных с устаревшими сигнатурами `sendMedia/sendMediaReply`, а не с текущими UI-правками.

## Примечания

- В рабочем дереве уже были пользовательские изменения в `java-bot/bot-max/logs/bot-telegram.log`, `spring-panel/bot_database.db`, `spring-panel/settings.db`; они не трогались.
