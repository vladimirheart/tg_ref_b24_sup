# 2026-05-20 13:35:00 - Sidebar footer avatar and notification fixes

## Промты пользователя

- `почти. аватара так и нет. спусти его тогда вниз сайдбара.`
- `а ещё есть 3 проблемы с оповещениями: 1. в них битая кодировка. 2. при клике на колокольчик, оповещени как будто под сайдбаром опускаются. 3. даже если кликнуть на пункт из оповещения, то счётчик остаётся таким-же, то есть количство непрочитанных оповещение не уменьшается`

## Что сделано

- В `spring-panel/src/main/resources/templates/fragments/navbar.html` блок авторизованного пользователя с avatar и именем перенесён из header обратно в footer sidebar, над icon-only действиями.
- В `spring-panel/src/main/resources/templates/fragments/ui-head.html` добавлены глобальные meta-теги `_csrf` и `_csrf_header`, чтобы sidebar notifications могли надёжно отправлять `read`-POST со всех страниц панели.
- В `spring-panel/src/main/resources/scss/sidebar/_shell.scss` для `.sidebar` снят `overflow: hidden`, чтобы notifications dropdown больше не клипался границами sidebar.
- В `spring-panel/src/main/resources/scss/sidebar/_notifications.scss` dropdown оповещений выровнен вправо относительно блока кнопок в развернутом sidebar.
- В `spring-panel/src/main/resources/static/js/sidebar.js` добавлены безопасные notification-helpers:
  - разделение общего массива `/api/notifications` на `unread` и `read` по `is_read`
  - unicode-safe подписи для dropdown без битой кириллицы
  - `keepalive`-запрос на `/api/notifications/{id}/read`
  - локальное уменьшение unread counter сразу после успешного клика
  - перевод bell refresh/polling на новый safe-path, который при открытом dropdown перерисовывает его через корректную unread/read модель

## Проверка

- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Что дальше

- Если avatar в footer всё ещё не виден у конкретного пользователя, значит в runtime avatar storage отсутствует сам файл по имени из `users.photo`; код теперь корректно нормализует путь и отдаёт fallback, дальше нужно восстанавливать asset-файл.
