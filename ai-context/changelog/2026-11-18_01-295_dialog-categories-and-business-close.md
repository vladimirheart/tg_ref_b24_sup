# 2026-11-18 17:00 MSK — Категории и закрытие бизнес-обращения в общей карточке

Задача01-295, baseline HEAD/origin/main
`e39dc3d2fd56f51881ea1cf9532cc7810c7e404f`, после свежего fetch; root clean.

Промт пользователя:

```text
репо обновлён многими изменениями. смотри, какая-то херня: я выбрал категории, они сохранились, но при закрытии диалога вернуло требование выбора категории - смотри на скрин
```

Скриншот показывает сохранённые категории в шапке BUSINESS и требование выбрать
категорию при закрытии. Подтверждено в коде и regression: summary уже читает
`tickets.business_categories`, а массив details ещё загружался из legacy таблицы.
Поэтому JS показывал fallback label, но проверял пустой выбор. Close endpoint
также направлял BUSINESS в legacy lifecycle вместо canonical work command.

Изменённые области: DialogConversationReadService и DialogDetailsReadService
читают canonical array по полному owner tuple, сохраняют значения с запятыми,
не подменяют отсутствие данных текстом шапки. BusinessTicketWorkService,
DialogQuickActionService и DialogQuickActionsController направляют закрытие в
существующий authenticated BUSINESS CAS с проверкой текущих прав и409 при конфликте;
пустой выбор по-прежнему отклоняется. Legacy fallback не добавлен.

Обновлены BusinessTicketWorkPostgresTest, DialogQuickActionsControllerWebMvcTest,
добавлен dialog-category-save-reopen-close.test.js; task-list/task-details01-295.
Точный список11 файлов — в карточке задачи.

Проверки:266 Java cases/5 suites и5 Node checks GREEN. Native PostgreSQL16.15,
nonce-owned test DB, реальные SQL/migrations/audit/CAS; MVC с CSRF и real handlers,
UI full-runtime с fetch/DOM doubles. Baseline assembler regression RED на пустом
массиве. Ошибка helper javac без `-parameters` исправлена в v2 и44 MVC cases
повторены GREEN;222 service/PostgreSQL cases прошли в первом полном прогоне.
Source build через targeted javac/JUnit с прежним dependency cache, полный Maven
suite и live browser/provider acceptance не выполнялись. Compile/diff/guards GREEN.

Проверено в detached sandbox и перенесено exact baseline/blob/source SHA/scope
guarded copy в основной checkout. Staging/commit/push/deployment нет. Рабочие
категории и .env не менялись, новых миграций/backfill нет. Test PostgreSQL остановлен.
Для применения нужен выпуск backend/panel-web и проверка закрытия пользователем.
