# 2026-04-14 18:54:26 - список диалогов, settings/dashboard UI и колокольчик sidebar

## Что изменено

- В списке диалогов добавлены цветовые плашки каналов, усилена цветовая
  дифференциация SLA и выведен аватар ответственного рядом с именем.
- Backend диалогов теперь обогащает ответственных профилями пользователей,
  чтобы список мог стабильно показывать display name и фото.
- Для оповещений sidebar добавлен более надёжный fallback по получателям и
  обновлена клиентская загрузка `bell`-dropdown с явными `same-origin`/CSRF
  параметрами.
- На странице `settings` плитки модулей сделаны компактнее: иконка и название
  собраны в одну строку, уменьшены отступы и смягчён visual weight.
- Модальные окна и открываемые секции `settings` получили более аккуратные
  surfaces, header/footer и таб-контент в более продуктовой подаче.
- Верхняя композиция `dashboard` перестроена в более живой analytics-board:
  тёмный hero, compact live-stats, более выраженный control-center и обновлённая
  подача KPI по мотивам live sales dashboard.

## Проверка

- `spring-panel\\mvnw.cmd -q -DskipTests compile`

## Затронутые файлы

- `ai-context/changelog/2026-04-14_185426_dialog-list-settings-dashboard-and-sidebar-notifications.md`
- `ai-context/tasks/task-list.md`
- `spring-panel/src/main/java/com/example/panel/model/dialog/DialogListItem.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogService.java`
- `spring-panel/src/main/java/com/example/panel/service/NotificationService.java`
- `spring-panel/src/main/resources/static/css/app.css`
- `spring-panel/src/main/resources/static/js/dialogs.js`
- `spring-panel/src/main/resources/static/js/sidebar.js`
- `spring-panel/src/main/resources/templates/dashboard/index.html`
- `spring-panel/src/main/resources/templates/dialogs/index.html`
- `spring-panel/src/main/resources/templates/settings/index.html`
