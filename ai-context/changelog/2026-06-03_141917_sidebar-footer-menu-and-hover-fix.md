# 2026-06-03 14:19:17 - sidebar footer menu and hover fix

## Промты пользователя

- `лавай-ка подкрутим.`
- `* в футере ужасно выгледят кнопки функций. сделай такую: https://21st.dev/community/components/chetanverma16/floating-action-menu/default`
- `* сайдбар разлелён на "Рабочий контур" и "Администрирование". в "Рабочий контур" помести "Диалоги", "AI Ops" , "Задачи", "Клинты", "Паспорт объектов", "База знаний". В "Администрирование" Перенеси "Отчёты", "Аналитика", "Настройки"`
- `* Выпили вообзе кнопку "Плотность интерфейса"`
- `* убери надпись "Очереди, уведомления и быстрый доступ к рабочим контурам."`
- `* проверь как работает разворачивание сайдбара - при наведении в свёрнутый сайдбар, лишь сдвигается страница, и отображается пустое пространство где должен быть сайдбар, но сам сайдбар не раскрывается. раскрывается только при его закреплении, что правильно`

## Что сделано

- В `spring-panel/src/main/resources/templates/fragments/navbar.html` из header sidebar убраны текст `Очереди, уведомления и быстрый доступ к рабочим контурам.` и кнопка `Плотность интерфейса`.
- Там же перегруппирована навигация: в `Рабочий контур` оставлены только `Диалоги`, `AI Ops`, `Задачи`, `Клиенты`, `Паспорт объектов`, `База знаний`, а секция `Администрирование` теперь начинается перед `Отчётами`, `Аналитикой`, `Настройками` и остальными admin/fallback-пунктами.
- В footer `navbar.html` обычные кнопки `Пароль / Порядок / Выход` заменены на компактный `floating action menu`-паттерн по мотивам рефа 21st.dev: список действий теперь раскрывается из одной плавающей trigger-кнопки.
- В `spring-panel/src/main/resources/static/js/sidebar.js` удалена логика, привязанная к `densityModeBtn`, добавлено управление новым action-menu (`open/close`, закрытие по outside click и `Escape`), а действия меню закрывают popover после выбора.
- В `spring-panel/src/main/resources/scss/sidebar/_shell.scss` добавлено правило `.sidebar.collapsed.hovering { width: var(--sidebar-w); }`, из-за которого hover-режим свёрнутого sidebar теперь действительно раскрывает сам sidebar, а не только сдвигает основной контент.
- В `spring-panel/src/main/resources/scss/sidebar/_sections.scss` переписан footer-блок под floating action menu: добавлены стили trigger-кнопки, вертикального списка action-pill, accent/danger-состояний и поведение для mobile/collapsed режимов.
- В `spring-panel/src/main/resources/scss/sidebar/_notifications.scss` и пересобранном `spring-panel/src/main/resources/static/css/sidebar.css` синхронизированы новые collapsed/mobile селекторы после удаления старых footer-кнопок.
- В `ai-context/tasks/task-list.md` задача `01-116` переведена в статус `🟣`, так как этот раунд правок по sidebar выполнен со стороны AI.

## Проверка

- `spring-panel\mvnw.cmd -q generate-resources`
- `git diff --check -- spring-panel/src/main/resources/templates/fragments/navbar.html spring-panel/src/main/resources/static/js/sidebar.js spring-panel/src/main/resources/scss/sidebar/_shell.scss spring-panel/src/main/resources/scss/sidebar/_sections.scss spring-panel/src/main/resources/scss/sidebar/_notifications.scss spring-panel/src/main/resources/static/css/sidebar.css ai-context/tasks/task-list.md`
  результат: SCSS успешно пересобран, ошибок форматирования в diff нет; Git показал только ожидаемые предупреждения о LF/CRLF в рабочей копии Windows.

## Что дальше

- Полезно открыть живую панель и руками проверить три сценария: hover у незакреплённого sidebar на desktop, раскрытие нового footer action menu в pinned/collapsed состояниях и mobile drawer после удаления `density`-кнопки.
