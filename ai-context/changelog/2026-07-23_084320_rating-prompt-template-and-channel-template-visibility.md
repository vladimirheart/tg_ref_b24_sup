# 2026-07-23 08:43:20 — rating prompt template and channel template visibility

- Затронутые файлы:
  - `spring-panel/src/main/java/com/example/panel/service/DialogNotificationService.java`
  - `spring-panel/src/main/resources/static/js/settings-channels-shell-runtime.js`
  - `spring-panel/src/main/resources/static/js/settings-channels-catalog-runtime.js`
  - `spring-panel/src/test/java/com/example/panel/service/DialogNotificationServiceTest.java`
  - `ai-context/tasks/task-details/01-150.md`

- Промт пользователя:
  - `посмотри задачу 01-150. по ней я всё ещё не вижу активного шаблона вопросов и текущего шаблона оценок, т.к. в вопросах клиенту сечас приходит пул вопросов и вариантов, а в оценках, 5 приходит с другим ответом нежели отображается в шабоне на странице настроек`

- Что сделано:
  - В `DialogNotificationService` убран hardcoded prompt оценки после закрытия диалога: panel теперь берёт текст из canonical `bot_settings.rating_templates` и учитывает `channel.rating_template_id`, чтобы prompt оценки и bot feedback response опирались на один template contract.
  - Добавлен `DialogNotificationServiceTest`, который проверяет active rating template, channel override и fallback при битом override.
  - В channels UI добавлена явная видимость effective question/rating/auto templates по каждому боту; stale template override теперь подсвечивается как warning вместо молчаливого fallback.
  - В `01-150` зафиксирован новый execution slice: найден и закрыт runtime bypass для rating prompt, плюс добавлена diagnostics-видимость channel template selection для live/manual verification.
