# 2026-07-31 09:28:09 - bot-template-live-sync-and-save-ux

## Затронутые области

- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-157.md`
- `spring-panel/src/main/resources/static/js/bot-settings.js`
- `spring-panel/src/main/resources/static/js/settings-channel-templates-runtime.js`
- `spring-panel/src/main/resources/static/js/settings-channels-shell-runtime.js`
- `spring-panel/src/main/resources/static/js/settings-channel-editor-persistence-runtime.js`
- `spring-panel/src/main/resources/static/js/settings-save-runtime.js`

## Пользовательский промт

> создал новый шаблон вопросов, пытаюсь в боте выбрать его, но не отображается. стал отображаться только после обновления страницы.
>
> если есть какие-то ошибки сохранения, инфо об этом выводится, но серым цветом. перекрась в красный.
>
> если сохраняю любое изменение и нет ошибок, бабл, где проводится сохранение, должен закрываться, как это сделано на редактировании шаблонов вопросов

## Что сделано

- Добавлен task `01-157` и оформлена детализация по проектным правилам.
- В `bot-settings.js` добавлена live-синхронизация связанных runtime после сохранения шаблона/настроек бота, исправлена визуализация status line и добавлено автозакрытие основной модалки после успешного save.
- В `settings-channel-templates-runtime.js` каталог шаблонов сделан обновляемым без перезагрузки страницы.
- В `settings-channels-shell-runtime.js` добавлен `refreshTemplateCatalog`, чтобы каналовые селекты и открытый редактор канала получали свежие шаблоны сразу после сохранения.
- В `settings-channel-editor-persistence-runtime.js` popup-уведомления получили типы `error/success`, а редактор канала теперь закрывается после успешного сохранения.
- В `settings-save-runtime.js` popup-уведомления сохранения переведены на явные типы `error/success/warning`, чтобы ошибки больше не отображались как нейтральные.
