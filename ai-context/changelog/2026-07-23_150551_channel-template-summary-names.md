# 2026-07-23 15:05:51 — channel template summary names

- Затронутые файлы:
  - `spring-panel/src/main/resources/static/js/settings-channel-templates-runtime.js`
  - `ai-context/tasks/task-details/01-150.md`

- Промпт пользователя:
  - `я всё ещё не вижу в настройках "Базовый шаблон вопросов", хотя в настройке бота орн есть и работает`

- Что сделано:
  - Найден UI drift в summary шаблонов каналов: runtime резолвил правильный `question_template_id`, но показывал только детали шаблона без `template.name`.
  - `settings-channel-templates-runtime.js` обновлён так, чтобы summary для вопросов, оценок и автодействий всегда включал имя шаблона в формате `имя - детали`.
  - В `01-150` добавлена отдельная execution-note про этот user-facing хвост, чтобы было видно, что проблема была именно в отображении названия, а не в выборе шаблона ботом.
