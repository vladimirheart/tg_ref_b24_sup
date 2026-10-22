# 2026-10-22 15:25:07 — Neutral Ops для диалогов и операционной аналитики

## Промты пользователя

> мне категорически не нравятся темы оформления да и в целом внешний вид проекта. найди в сети примеры админок и дай рекомендации по изменениям

> тормози. не нужно сейчас ничего в проекте делать. дай просто превью. или сверстай страницу и дай в файле, чтобы я мог открыть её как автономно сохранённую страницу

> да, так намного приятнее. вот теперь сверстай задачу

## Изменённые области

- `spring-panel/src/main/resources/scss/app/_neutral-ops.scss`
- `spring-panel/src/main/resources/scss/app.scss`
- `spring-panel/src/main/resources/static/css/app.css`
- `spring-panel/src/main/resources/templates/dialogs/index.html`
- `spring-panel/src/main/resources/templates/dashboard/index.html`
- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-281.md`

## Фактические изменения

- Добавлен отдельный scoped SCSS-слой `Neutral Ops`, который убирает лишнюю
  декоративность и уплотняет ключевые рабочие зоны: очередь и workspace
  диалогов, KPI, фильтры, табы и панели dashboard.
- Слой активируется только атрибутом `data-ui-visual="neutral-ops"` в
  шаблонах диалогов и dashboard; сохранены существующие DOM-идентификаторы,
  runtime-классы и поведение.
- Пересобран производный `static/css/app.css`; в шаблоны добавлена новая
  cache-busting версия CSS.
- Не менялись Java, JavaScript, контроллеры, API, данные, миграции и
  конфигурация runtime.

## Валидация

- `.\mvnw.cmd -q generate-resources` — успешно.
- `.\mvnw.cmd -q -DskipTests compile` — успешно.
- `.\mvnw.cmd -q "-Dtest=DialogsControllerWebMvcTest,DashboardControllerWebMvcTest" test` — успешно: 9 тестов, 0 ошибок и 0 падений.
- `git diff --check` — успешно; только стандартные предупреждения Git о LF/CRLF.
