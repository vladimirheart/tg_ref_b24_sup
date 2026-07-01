# 2026-07-01 18:21:00 — Calm Ops enterprise UI pass

## Связанные задачи

- `01-137` — Провести enterprise UI-проход Iguana в стиле Calm Operations без изменения продуктовой логики

## Пользовательский промпт

> Проект: **Iguana** — support CRM / операторская панель для обработки обращений из Telegram, VK и MAX.  
> Нужно сделать UI-итерацию в направлении **Enterprise support cockpit / Calm Operations**.  
> Главная цель: сделать интерфейс более строгим, плотным, операторским и менее декоративным, без радикальной смены архитектуры и без удаления существующих функций.

Ключевые ограничения из запроса:

- compact-first для dialogs, списков, workspace, таблиц и AI Ops event log;
- уменьшить декоративность, радиусы, тени и glossy-градиенты на рабочих экранах;
- сохранить зелёную calm-tech айдентику Iguana, но развести семантические цвета по ролям;
- привести dialogs, dashboard, ai-ops, settings, navbar и knowledge base к более профессиональному support-operations виду;
- править SCSS-источники, если они есть, и обновить CSS через принятый workflow;
- не ломать routes/controllers/Thymeleaf/Bootstrap modals/collapse/dropdowns.

## Затронутые файлы

- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-137.md`
- `spring-panel/src/main/resources/scss/style/_theme.scss`
- `spring-panel/src/main/resources/scss/app/_core.scss`
- `spring-panel/src/main/resources/scss/app/_unified-ui.scss`
- `spring-panel/src/main/resources/scss/app/_dialogs.scss`
- `spring-panel/src/main/resources/scss/app/_knowledge.scss`
- `spring-panel/src/main/resources/scss/sidebar/_shell.scss`
- `spring-panel/src/main/resources/scss/sidebar/_sections.scss`
- `spring-panel/src/main/resources/templates/dialogs/index.html`
- `spring-panel/src/main/resources/templates/dialogs/ai-ops.html`
- `spring-panel/src/main/resources/templates/dashboard/index.html`
- `spring-panel/src/main/resources/templates/settings/index.html`
- `spring-panel/src/main/resources/templates/knowledge/list.html`
- `spring-panel/src/main/resources/templates/knowledge/editor.html`
- `spring-panel/src/main/resources/templates/fragments/navbar.html`
- `spring-panel/src/main/resources/static/css/style.css`
- `spring-panel/src/main/resources/static/css/app.css`
- `spring-panel/src/main/resources/static/css/sidebar.css`

## Что сделано

- В theme/token-слое уменьшены page paddings, panel radius и тени; усилены границы поверхностей; calm green palette сделана строже; добавлены более явные semantic state variables для system/warning/danger/ai/success/neutral.
- В общем UI-слое приведены к единому виду page headers, tables, badges, buttons, outline-actions и служебные surface-компоненты; добавлены reusable utility-классы для KPI/log/tag presentation.
- Sidebar переведён в более строгий admin/support вид: меньше стеклянности и градиентов, компактнее shell/header/nav, яснее active state, обновлён brand caption.
- Dialogs уплотнены как inbox/workspace: compact density включён через data-атрибут, toolbar и table стали плотнее, AI Ops CTA переведён в отдельную AI-семантику, блок списка получил явный inbox header, open-action усилен как primary.
- AI Ops оформлен как technical control center: compact density, tokenized tabs, KPI-grid, строгие section cards, плотный filterbar и логовый визуальный ритм для alerts/events/memory.
- Settings успокоены за счёт override-слоя поверх существующего inline-style: убраны декоративные glow/hero-элементы, overview и tiles стали площе и строже, tile icons переведены с emoji на Bootstrap Icons, заголовок и copy сделаны более admin-oriented.
- Knowledge base приведена ближе к knowledge workspace: введён scoped knowledge SCSS, page-shell/header cards, calmer index/import cards, editor main/side surfaces и source chip для external metadata.
- SCSS пересобран штатным Maven pipeline в `static/css/app.css`, `static/css/style.css`, `static/css/sidebar.css`.

## Проверки

- `.\mvnw.cmd -q generate-resources`
  - результат: SCSS успешно пересобран через `dart-sass-maven-plugin`.
- `.\mvnw.cmd -q -DskipTests compile`
  - результат: проект компилируется.
- `.\mvnw.cmd -q -Dtest=DialogsControllerWebMvcTest test`
  - результат: WebMvc test прошёл, шаблон dialogs/navbar/ai-ops не сломан.
- `git diff --check -- spring-panel/src/main/resources/scss spring-panel/src/main/resources/templates ai-context/tasks/task-list.md ai-context/tasks/task-details/01-137.md`
  - результат: ошибок diff-formatting нет; Git показал только стандартные предупреждения LF/CRLF для Windows-рабочей копии.

## Примечания

- В рабочем дереве уже были пользовательские изменения в `java-bot/bot-max/logs/bot-telegram.log`, `spring-panel/bot_database.db`, `spring-panel/settings.db`; они не трогались.
