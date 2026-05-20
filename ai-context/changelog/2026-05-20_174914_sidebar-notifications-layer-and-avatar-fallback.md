# 2026-05-20 17:49:14 - Sidebar notifications layer and avatar fallback

## Промпты пользователя

- `оповещения всё равно всплывают под кнопками навигации в сайдбаре.`
- `оповещение якобы становится прочитанным только по клику по нему хотя достаточно простого прочтения`
- `аватар так и не появился, хотя он точно есть, как минимум отображение корректно в списке диалогов в столбце ответственного. сделай какую-то штатную иконку аватара, если он не загружен в профиле`

## Что сделано

- В `spring-panel/src/main/resources/static/js/sidebar.js` dropdown уведомлений вынесен из DOM sidebar в `document.body` и теперь позиционируется через `position: fixed` от кнопки колокольчика. Это убирает наложение списка под навигационные кнопки и делает слой независимым от layout/sidebar clipping.
- В `spring-panel/src/main/resources/scss/sidebar/_notifications.scss` и `spring-panel/src/main/resources/static/css/sidebar.css` для `.notify-dropdown` закреплено фиксированное позиционирование и повышен `z-index`, чтобы меню уведомлений гарантированно рисовалось поверх sidebar.
- В `spring-panel/src/main/java/com/example/panel/controller/NotificationApiController.java`, `service/NotificationService.java` и `repository/NotificationRepository.java` добавлен endpoint `POST /api/notifications/read-all` и bulk-логика пометки всех непрочитанных уведомлений как прочитанных.
- В `spring-panel/src/main/resources/static/js/sidebar.js` открытие dropdown теперь после загрузки сразу вызывает bulk-read и локально перестраивает список так, чтобы уведомления считались прочитанными уже по факту просмотра, а не только по клику на конкретный пункт.
- В `spring-panel/src/main/java/com/example/panel/service/NavigationService.java` добавлен флаг `sidebarUserHasAvatar`, чтобы шаблон sidebar различал реальное фото и fallback-состояние.
- В `spring-panel/src/main/resources/templates/fragments/navbar.html` блок пользователя в footer sidebar переведён на штатный встроенный fallback-аватар с SVG-иконкой, который показывается, если фото отсутствует или не загрузилось.
- В `spring-panel/src/main/resources/scss/sidebar/_sections.scss` и `spring-panel/src/main/resources/static/css/sidebar.css` добавлены стили fallback-аватара и состояние `is-loaded`, чтобы реальная фотография перекрывала иконку только после успешной загрузки.
- В `ai-context/tasks/task-list.md` добавлена отдельная запись `01-099` про добивку слоя уведомлений и fallback-аватара.

## Проверка

- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Что дальше

- Если у текущего пользователя путь к фото в профиле всё ещё ведёт на отсутствующий runtime-файл, sidebar теперь покажет штатную иконку вместо пустого места; для возврата именно фотографии останется проверить наличие самого avatar asset по пути из `users.photo`.
