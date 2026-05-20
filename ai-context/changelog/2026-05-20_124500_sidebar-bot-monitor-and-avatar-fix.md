# 2026-05-20 12:45:00 - Sidebar bot monitor and avatar fix

## Промты пользователя

- `в сайдбаре был прикручен мониторинг состояния ботов на кнопке страницы "Каналы" вынеси этот мониторинг в шапку сайдбара.`
- `"показать аватар и профиль пользователя в шапке sidebar под тестом OPS" эта задача не выполнена до конца - я не вижу аватара`

## Что сделано

- В `spring-panel/src/main/resources/templates/fragments/navbar.html` индикатор состояния ботов убран из пункта `Каналы` и перенесён в шапку sidebar отдельной строкой под блоком пользователя.
- В том же `navbar.html` для изображения пользователя добавлен `onerror` fallback на `/avatar_default.svg`, чтобы broken avatar не оставлял пустое место в header.
- В `spring-panel/src/main/resources/scss/sidebar/_sections.scss` добавлены стили для новой header-плашки мониторинга ботов и поведение в collapsed sidebar.
- В `spring-panel/src/main/java/com/example/panel/service/NavigationService.java` исправлен `resolveAvatarUrl(...)`: теперь sidebar корректно поддерживает абсолютные URL, уже сохранённые `/api/attachments/avatars/...`, legacy `avatars/...` и голые имена файлов.
- Тот же avatar URL fallback синхронизирован в `spring-panel/src/main/java/com/example/panel/service/DialogLookupReadService.java`, чтобы отображение аватаров операторов не расходилось между sidebar и диалоговыми представлениями.
- После пересборки обновлён `spring-panel/src/main/resources/static/css/sidebar.css`.

## Проверка

- `spring-panel\mvnw.cmd -q -DskipTests compile`

## Что дальше

- Если после этих правок конкретный пользователь всё ещё без аватара, следующим шагом стоит проверить, что у его записи в `users.photo` реально сохранено значение, а не пустая строка.
