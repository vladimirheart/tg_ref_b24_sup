# 2026-06-03 17:00:37 - sidebar footer row and usersdb avatar source fix

## Промты пользователя

- `уже лучше. в футере опусти аккаунт на один уровень с floating action menu.`
- `аватар у пользователя не отображается. проверь, верно-ли он читает данные - они должны читаться со страницы настроек из блока "Пользователи панели и настройки доступа"`

## Что сделано

- В `spring-panel/src/main/resources/templates/fragments/navbar.html` footer sidebar перестроен в новый layout `sidebar-footer-main`: account block и floating action menu теперь находятся на одном визуальном уровне, а не идут лесенкой друг под другом.
- В `spring-panel/src/main/resources/scss/sidebar/_sections.scss` добавлены стили `sidebar-footer-main` и `sidebar-account-block`, чтобы account-card занимал левую часть footer, а action menu оставался справа; на мобильной ширине layout снова складывается в колонку.
- В `spring-panel/src/main/java/com/example/panel/repository/PanelUserRepository.java` исправлен источник данных sidebar-пользователя: репозиторий теперь явно инжектит `@Qualifier("usersJdbcTemplate")`, то есть читает из той же `users`-базы и того же блока `Пользователи панели и настройки доступа`, что и страница настроек.
- После обновления SCSS пересобран `spring-panel/src/main/resources/static/css/sidebar.css`.

## Проверка

- `spring-panel\mvnw.cmd -q generate-resources`
- `spring-panel\mvnw.cmd -q -DskipTests compile`
- `git diff --check -- spring-panel/src/main/resources/templates/fragments/navbar.html spring-panel/src/main/resources/scss/sidebar/_sections.scss spring-panel/src/main/resources/static/css/sidebar.css spring-panel/src/main/java/com/example/panel/repository/PanelUserRepository.java`
  результат: SCSS и Java compile проходят; ошибок форматирования в diff нет, только стандартные предупреждения Git о LF/CRLF в Windows-рабочей копии.

## Что дальше

- После этого особенно полезно проверить живой sidebar под пользователем, у которого в настройках реально сохранено `photo`: теперь footer должен брать то же поле `users.photo`, что и modal/listing блока пользователей панели.
