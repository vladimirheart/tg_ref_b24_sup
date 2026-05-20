# 2026-05-20 13:05:00 - Sidebar avatar legacy paths and inline access

## Промты пользователя

- `продолжи`

## Что сделано

- Проверен реальный источник данных для sidebar-пользователя: `PanelUserRepository` читает таблицу `users` через `usersJdbcTemplate`, а не из пустого `spring-panel/users.db`.
- Выявлено, что в рабочем корневом `users.db` поле `users.photo` хранит legacy-значения вида `/static/user_photos/...jpg`.
- В `spring-panel/src/main/java/com/example/panel/service/NavigationService.java` добавлена нормализация legacy-путей `/static/user_photos/...` и `/user_photos/...` в актуальный endpoint `/api/attachments/avatars/{filename}`.
- Та же нормализация синхронизирована в `spring-panel/src/main/java/com/example/panel/service/DialogLookupReadService.java`, чтобы avatar URL для операторов не расходился между sidebar и диалоговыми представлениями.
- В `spring-panel/src/main/java/com/example/panel/storage/AttachmentService.java` avatar download переведён с `attachment` на inline-ответ и больше не требует `PAGE_CLIENTS`: теперь любой авторизованный пользователь может загрузить собственный avatar asset в sidebar на любой странице панели.

## Проверка

- Временным Java-проверочным скриптом подтверждено содержимое корневого `users.db`:
  `admin -> /static/user_photos/...jpg`
  `ShumkovaDA -> /static/user_photos/...jpg`
  `KandelakiDE -> /static/user_photos/...jpg`
- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Что дальше

- Если конкретные avatar-файлы физически отсутствуют в configured `app.storage.avatars`, sidebar теперь корректно покажет fallback `/avatar_default.svg`, но для возврата персонального фото нужно будет восстановить сами файлы в avatar storage.
