# 2026-04-14 12:48:00 - refresh sidebar, dashboard и общей UI-палитры

## Что изменено

- Добавлен task `[01-018]` и его детализация по обновлению sidebar,
  дашбордов и общего визуального слоя UI.
- Полностью переработан `sidebar`:
  добавлен выраженный brand-block,
  обзорный workspace-card,
  более насыщенные состояния навигации,
  новая иерархия подписей и более современный rail-стиль.
- Полностью обновлены стили `sidebar.css` под новую структуру фрагмента
  `navbar.html`, включая responsive-режим, collapsed-state и dropdown
  уведомлений.
- Освежена базовая neo-палитра в `style.css`: primary/secondary переведены в
  зелёно-янтарную гамму проекта, смягчён фон и усилены surface/shadow-эффекты.
- Страница `dashboard/index.html` перестроена в формат:
  hero-section,
  control-center с фильтрами и KPI,
  staff insights,
  единая grid-композиция графиков и аналитических карточек.
- Существующие `id` фильтров, KPI и canvas-элементов сохранены, чтобы не
  ломать текущий JS загрузки отчётов и графиков.

## Затронутые файлы

- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-018.md`
- `ai-context/changelog/2026-04-14_124800_sidebar-dashboard-ui-refresh.md`
- `spring-panel/src/main/resources/static/css/sidebar.css`
- `spring-panel/src/main/resources/static/css/style.css`
- `spring-panel/src/main/resources/templates/dashboard/index.html`
- `spring-panel/src/main/resources/templates/fragments/navbar.html`
