# 2026-06-03 14:34:27 - sidebar grouping and notification read fix

## Промты пользователя

- `вот это не выполнено:`
- `* сайдбар разлелён на "Рабочий контур" и "Администрирование". в "Рабочий контур" помести "Диалоги", "AI Ops" , "Задачи", "Клинты", "Паспорт объектов", "База знаний". В "Администрирование" Перенеси "Отчёты", "Аналитика", "Настройки"`
- `всё сейчас под "Администрирование"`
- `и ещё: в оповещениях нет разделения на прочитанные и не прочитанные, а когда открываешь список оповещений, то считаются прочитанными сразу все, а не только отображаемые на экране`

## Что сделано

- В `spring-panel/src/main/resources/templates/fragments/navbar.html` навигация переведена с плоского списка на реальные DOM-группы `sidebar-nav-group` с `data-nav-group="workspace"` и `data-nav-group="admin"`. Благодаря этому `Рабочий контур` теперь физически содержит только `Диалоги`, `AI Ops`, `Задачи`, `Клиенты`, `Паспорт объектов`, `База знаний`, а `Отчёты`, `Аналитика`, `Настройки` и admin/fallback-пункты остаются под `Администрирование`.
- В `spring-panel/src/main/resources/static/js/sidebar.js` логика `applyOrder(...)` обновлена так, чтобы reorder sidebar работал внутри новых DOM-групп и не переносил все `.nav-link` под второй section label.
- В том же `sidebar.js` из `loadNotificationsSafe()` удалена массовая пометка `/api/notifications/read-all` при открытии dropdown. Теперь открытие списка не переводит все unread в read автоматически.
- Разделение `Непрочитанные` / `Прочитанные` сохранено в рендере dropdown и после удаления `read-all` снова остаётся видимым, пока пользователь действительно не открывает конкретное уведомление.
- В `spring-panel/src/main/resources/scss/sidebar/_sections.scss` и пересобранном `spring-panel/src/main/resources/static/css/sidebar.css` добавлен стиль `.sidebar-nav-group`, чтобы новая группировка не ломала вертикальный ритм sidebar.

## Проверка

- `spring-panel\mvnw.cmd -q generate-resources`
- `git diff --check -- spring-panel/src/main/resources/templates/fragments/navbar.html spring-panel/src/main/resources/static/js/sidebar.js spring-panel/src/main/resources/scss/sidebar/_sections.scss spring-panel/src/main/resources/static/css/sidebar.css`
  результат: SCSS успешно пересобран, ошибок форматирования в diff нет; остались только стандартные предупреждения Git про LF/CRLF в рабочей копии Windows.

## Что дальше

- Если нужно, следующим шагом можно отдельно доработать UX уведомлений так, чтобы unread помечались прочитанными не только по клику по ссылке, но и по явному пользовательскому действию внутри dropdown для non-link уведомлений.
