# 01-278: точный ticket ID в контексте макросов (R18)

Дата и время: 2026-10-11 13:14:58, Europe/Moscow.

Промт пользователя: `давай дальше, но немного меньшим пакетом`

## Фактические изменения

- Пакет ограничен макросами: два вызова trim удалены из
  `DialogMacroService`. Dry-run и каталог переменных используют исходный
  непустой `ticketId` до существующего защищённого details lookup.
  ` T-EXACT `, ` T-EXACT`, `T-EXACT ` и `T-EXACT` не объединяются.
- В lookup используется существующая цепочка details → lookup → shared
  JDBC guard. Missing/duplicate exact ID не открывает дочерние данные и
  client profile; другой ticket не подставляется за счёт нормализации ID.
  Новый guard и constructor dependency не добавлялись.
- Сохранены нетикетные/global macros, configured defaults, приоритет
  request variables, нормализация ключей переменных, renderer/fallback
  behavior и разрешённый preview с явно переданными значениями.
  Request preview и legacy display business label не являются authority.

## Проверки

- Свежий `HEAD=origin/main=823178c71e29b522e67a96c05cf370bf58a098ba`;
  parent `5b708ca2ebefd0248de1efcbd78972c0b59c926a`. Commit R17 проверен
  по SHA, parent, message и фактическим 14 изменённым файлам. Root checkout
  чистый; remote main повторно проверен через fetch перед переносом.
- Изменения сначала подготовлены в detached temporary worktree.
  Repository Maven wrapper: `35/35` tests, 5 классов, failures/errors/skipped
  = 0. Новые `DialogMacroServiceTest`: `17/17`; existing macro WebMvc:
  `5/5`; details: `2/2`; lookup: `9/9`; shared JDBC guard: `2/2`.
- Из новых tests три используют реальный PostgreSQL guard и настоящую
  details/lookup цепочку: missing ID, duplicate ID и неизвестный padded
  сосед plain ticket. Проверяется отсутствие child JDBC/history/profile
  interactions. Остальные покрывают exact context/catalog, plain ID,
  отсутствие ticket scope, defaults и overrides.
- Первый запуск на исходном коде: 17 tests, 2 assertion failures по
  преобразованию ID и 10 fixture errors (mock final Java record).
  Fixture исправлена отдельным v2 patch на настоящий record без изменения
  Mockito configuration и без ослабления assertions. Финальный run GREEN.
- `git diff --check` и exact changed-file set проверяются до переноса;
  применяются те же deterministic patches с LF/CRLF-normalized сверкой
  с sandbox. Generated CSS не входит в пакет: после Maven только EOL
  checkout, semantic diff отсутствует.
- Полный `PanelApplicationTests` в R18 не запускался: constructor/wiring
  и схема не менялись. WebMvc context, Java compile и целевые tests GREEN.

## Границы

- Это source-only S2A checkpoint, не завершение 01-278; статус 🟡.
- Telemetry service, API contracts, права, business assignment и strict
  filtering не менялись. Business owner не определяется из user/channel.
- R12 provenance остаётся `PROPOSED_NOT_APPROVED`; S2B approvals, aliases,
  role matrix и shared-channel decisions остаются PENDING.
- Рабочая/production БД, Flyway migration вне тестовых schemas, backfill,
  feature flags, deploy, staging, commit и push агентом не выполнялись.
- Следующий небольшой пакет — отдельный exact-ID review telemetry lookup
  на свежем main с учётом пользовательских изменений.

## Изменённые файлы

- `spring-panel/src/main/java/com/example/panel/service/DialogMacroService.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogMacroServiceTest.java`
- `ai-context/tasks/task-details/01-278.md`
- `ai-context/changelog/2026-10-11_131458_01-278_macro_exact_ticket_identity.md`

