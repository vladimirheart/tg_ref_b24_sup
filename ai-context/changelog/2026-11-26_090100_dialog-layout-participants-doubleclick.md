# 01-320 — Высота окна диалога, участники и двойной клик

Промт пользователя:

> 1. в окне диалога при разворачивании на узком по высоте масштабе, не растягивается инфо из блока "статус"
> 2. участники диалога не отображаются из-за высоты окна.\
>    плюс при попытке добавить кого-то, возвращает "диалог не найден"
> 3. сделать открытие диалога не просто по кнопке, но ещё и по даблклику по строке диалога

Пользователь приложил два скриншота с обрезанным «Статусом» и picker участников.

## Изменения

- Секции слева сохраняют полную высоту при раскрытии и прокручиваются общей
  колонкой. Picker участников разворачивается внутри scrollable modal body;
  кнопки header/footer доступны при малой высоте. Карточки участников компактно
  выровнены, список в шапке диалога не увеличивает высоту по числу участников.
- BUSINESS manager использует canonical full-owner roster и scoped candidates,
  с JSON adapter существующего BusinessTicketParticipantService. Прежний путь
  отправлял legacy raw-ID запросы и не был подключён к отдельному business roster.
  POST/DELETE передают полный owner и roster revision, сохраняют PAGE_DIALOGS,
  CSRF, current READ/OPERATE, bound Authentication и транзакционные проверки.
  IDs/revisions — строки; клиентский signed owner и account BIGINT не смешиваются.
- Нет global/legacy fallback, импорта прежних participants, новых grants или SQL
  изменений. Read-only/closed показывают список без mutation actions. Stale owner,
  revision, ответы и ошибки не подменяют другое обращение; 409 не повторяется.
  Добавлена кнопка повторного чтения участников. Исторические members видимы по ID.
- Двойной клик по обычной ячейке таблицы использует тот же openDialogEntry, что
  кнопка. Интерактивные controls и modifier gestures сохраняют свои действия.
  Обновлены версии изменённых ресурсов.

## Валидация и границы

- 38 Java tests в 3 suites, 6 JS test files, syntax/SCSS/JAR/diff checks — GREEN.
- 12 Edge viewport/theme scenarios: 1280×360/540, 1920×700, 768×480, 390×640,
  1440×1000 × light/dark, с настоящими MVC HTML/JS/CSS и loopback API fixtures.
  Проверены status height/overlap, picker/roster, add/delete exact ID/revision,
  stale conflicts/readonly, оба способа открытия и viewport bounds.
- JAR содержит проверенные 5 runtime resources и 2 compiled classes.
- Отдельный PostgreSQL case DialogParticipantLegacyTicketGuardTest не запущен:
  Docker environment недоступен. Новых SQL statements нет; реальные locks/rollback
  не объявляются проверенными. Первоначальный RED сохранён отдельным log.
- Baseline 4aca18b74e2dce9abd22d4c827195d1db17f2082; isolated worktree и guarded
  перенос в рабочую копию. Нет staging/commit/push/deploy, миграций или live DB
  операций. Задача 01-320 — 🟣, ожидается ручная проверка.
