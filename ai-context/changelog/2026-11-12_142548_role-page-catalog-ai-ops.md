# Актуализация страниц в правах ролей и пользователей — 01-283

- Время: 12 ноября 2026 года, 14:25:48 Europe/Moscow.
- Baseline / HEAD / локальный origin/main: `cb7f57374a78b5a908b4719744f75a4d32a16437`.
- Статус задачи: 🟣 — ожидается проверка пользователя.

Промт пользователя:

```text
в настройках роли в блоке доступа к страницам есть устаревшие страницы, например "Каналы", "Пользователи и роли" и нет новой страницы "AI Ops". проверь корректность отображаемых страниц
```

## Изменения

- В PanelAccessPolicy введён единый упорядоченный каталог текущих страниц. Он используется API ролей, персональными правами и загрузкой полномочий при входе. Отдельные старые пункты Каналов и Пользователей/ролей убраны из текущего каталога; связанные страницы Задач/Проектов и Базы знаний/AI-источников названы явно. Общие правила Главной/Инцидентов поясняются в интерфейсе.
- AI Ops выделен в PAGE_AI_OPS: страница, ссылка меню, общие API мониторинга, очереди проверки, памяти решений и offline evaluation. Bootstrap администратора дополнен новым правом. PAGE_DIALOGS само по себе не открывает AI Ops; PAGE_AI_OPS не позволяет отвечать в Диалогах.
- Проверки точного ID обращения и CSRF сохранены. Экспорт событий отдельного диалога и AI-операции с конкретным обращением доступны по соответствующим существующим правам Диалогов. Переход из AI Ops в диалог отключён без права Диалогов.
- Старые сохранённые права каналов/пользователей остаются совместимыми. Сохранение актуальных страниц роли или её описания не удаляет скрытые старые разрешения. Названия персональных прав приходят из общего серверного каталога. Обновлены версии JS в шаблонах.
- Добавлены и обновлены серверные проверки каталога, индивидуальных прав и границ AI Ops; браузерный сценарий использует каталог из реального MVC-ответа. Обновлены матрица прав и карточка задачи.

## Изменённые области

- `spring-panel/src/main/java/com/example/panel/security/{PanelAccessPolicy,UserRepositoryUserDetailsService,SecurityBootstrap}.java`.
- `spring-panel/src/main/java/com/example/panel/controller/{AuthManagementApiController,UserPermissionsController,DialogsController,DialogAiOpsController}.java`.
- `spring-panel/src/main/java/com/example/panel/service/{NavigationService,DialogAuthorizationService}.java`.
- `spring-panel/src/main/resources/static/js/{auth-management,user-permissions,ai-ops}.js`.
- `spring-panel/src/main/resources/templates/{settings/index,users/index,dialogs/ai-ops,fragments/navbar}.html`.
- Соответствующие Java-тесты, новый `PagePermissionBoundaryWebMvcTest.java`, Node-тест `ai-ops-exact-legacy-ticket-id-r33.test.js`, `tools/page-permissions-qa.cjs`.
- `docs/settings-access-audit-2026-11-12.md`, `ai-context/tasks/task-list.md`, `ai-context/tasks/task-details/01-283.md`, эта запись.

## Проверка и границы

- Итоговые Surefire XML 13 целевых наборов: 368 тестов, 0 failures/errors/skipped. Финальная отдельная перепроверка нового PagePermissionBoundaryWebMvcTest: 21 тест, exit 0.
- Node AI Ops: 33 сценария GREEN. Playwright на 320/1440 px: права роли/пользователя, режим просмотра, сохранение legacy, изменение только описания, wildcard — GREEN. Скриншоты находятся в игнорируемом `spring-panel/target/page-permissions-qa/`.
- Синтаксис изменённых JS и `git diff --check`: GREEN. Изменения CSS только из-за окончаний строк после Sass восстановлены; содержимое CSS не менялось.
- source_applied=true; staged=false; committed=false; pushed=false; deployment=false. Проверки выполнялись в текущем checkout, отдельный sandbox не создавался. Рабочие данные, назначения ролей и внешние системы не изменялись.
- Для применения нужна пересборка панели и назначение AI Ops обычным ролям/пользователям без wildcard всех страниц. Миграция схемы не нужна.
