# 2026-06-03 14:51:09 - sidebar navgroup selector and fab layer fix

## Промты пользователя

- `теперь в блоке "Рабочий контур" творится какая-то несуразится - смотри скрин.`
- `floating action menu при клике ничего не разворачивает, возможно разворачивает за сайдбаром или всей страницы`

## Что сделано

- В `spring-panel/src/main/resources/static/js/sidebar.js` исправлен селектор `navGroups`: вместо общего `[data-nav-group]` теперь используются только контейнеры `.sidebar-nav-group[data-nav-group]`. Это убирает коллизию, когда в карту групп попадали и сами ссылки `.nav-link`, из-за чего reorder/sidebar-grouping ломал DOM внутри `Рабочего контура`.
- В `spring-panel/src/main/resources/scss/sidebar/_sections.scss` для `sidebar-footer` включён `overflow: visible`, чтобы раскрывающийся footer action menu не клипался контейнером footer.
- В том же `_sections.scss` для `sidebar-action-menu-shell` и `sidebar-action-menu-list` поднят `z-index`, чтобы floating action menu рисовался поверх footer/sidebar слоёв, а не визуально терялся за ними.
- После этого пересобран `spring-panel/src/main/resources/static/css/sidebar.css`.

## Проверка

- `spring-panel\mvnw.cmd -q generate-resources`
- `git diff --check -- spring-panel/src/main/resources/static/js/sidebar.js spring-panel/src/main/resources/scss/sidebar/_sections.scss spring-panel/src/main/resources/static/css/sidebar.css`
  результат: SCSS успешно пересобран, ошибок форматирования в diff нет; Git показал только стандартные предупреждения о LF/CRLF для Windows-рабочей копии.

## Что дальше

- Нужна живая проверка в браузере: после фикса action menu должен раскрываться поверх footer, а `Рабочий контур` — больше не собирать ссылки в кривую вложенность.
