# 2026-04-15 12:40:20 - замена дополнительных тем на catppuccin и amber minimal

## Что изменено

- Сохранена штатная проектная палитра `Iguana / neo` как основной вариант темы.
- Удалена старая дополнительная палитра `Mono Minimal`.
- В `style.css` добавлены две новые палитры:
  - `Catppuccin`
  - `Amber Minimal`
- Для обеих новых палитр заданы совместимые light/dark токены через уже
  существующую схему `theme + palette`.
- Обновлён `theme.js`: runtime теперь поддерживает только `neo`,
  `catppuccin` и `amber-minimal`, а fallback возвращается к `neo`.
- Обновлён UI в `settings`: вместо `Mono Minimal` пользователю предлагаются
  `Штатная тема Iguana`, `Catppuccin` и `Amber Minimal`.
- Структура, типы и общая подача диаграмм на `dashboard` не менялись: затронуты
  только theme tokens, чтобы сохранить текущую аналитику и visual rhythm.

## Проверка

- `spring-panel\\mvnw.cmd -q -DskipTests compile`

## Затронутые файлы

- `ai-context/tasks/task-list.md`
- `ai-context/tasks/task-details/01-023.md`
- `ai-context/changelog/2026-04-15_124020_replace-extra-themes-with-catppuccin-and-amber-minimal.md`
- `spring-panel/src/main/resources/static/css/style.css`
- `spring-panel/src/main/resources/static/js/theme.js`
- `spring-panel/src/main/resources/templates/settings/index.html`
