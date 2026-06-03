# 2026-06-03 15:09:22 - sidebar header distraction and footer avatar pass

## Промты пользователя

- `в хэдере надпись "ops" призывает к клику по нему, и этим отвелкает. убери её.`
- `подними кнопку фиксации сайдбара на уровень колокольчика.`
- `в футере не отображается иконка аватара авторизованного пользователя панели`

## Что сделано

- В `spring-panel/src/main/resources/templates/fragments/navbar.html` убран визуально отвлекающий badge `ops` из header sidebar.
- В `spring-panel/src/main/resources/scss/sidebar/_sections.scss` у верхнего блока действий отключён перенос (`flex-wrap: nowrap`) и добавлен `flex-shrink: 0`, чтобы кнопка фиксации sidebar больше не падала на следующую строку и оставалась на одном уровне с колокольчиком.
- В `spring-panel/src/main/resources/templates/fragments/navbar.html` user-card footer больше не зависит от `th:if="${#authentication?.name}"`, поэтому fallback-аватар и базовый account block рендерятся стабильно даже без фото пользователя.
- В `spring-panel/src/main/resources/scss/sidebar/_sections.scss` для `.sidebar-user-card` задан `min-height`, чтобы account-card не схлопывался визуально и в footer всегда оставалось место под avatar/fallback.
- После этого пересобран `spring-panel/src/main/resources/static/css/sidebar.css`.

## Проверка

- `spring-panel\mvnw.cmd -q generate-resources`
- `git diff --check -- spring-panel/src/main/resources/templates/fragments/navbar.html spring-panel/src/main/resources/scss/sidebar/_sections.scss spring-panel/src/main/resources/static/css/sidebar.css`
  результат: SCSS успешно пересобран, ошибок форматирования в diff нет; Git показал только стандартные предупреждения про LF/CRLF для Windows-рабочей копии.

## Что дальше

- Нужна живая проверка в браузере: после фикса badge должен исчезнуть, `pin` остаться в одном верхнем action-row, а footer account-card показать fallback-аватар даже если у пользователя нет загруженного фото.
