# 01-278: точный ticket ID в AI Ops UI (R33)

Дата и время: 2026-10-12 11:38:12, Europe/Moscow.

Промт пользователя: `дальше`

## Фактический scope

- Source-only продолжение R32: четыре ticket-scoped чтения в ai-ops.js
  больше не возвращают trimmed ID. Review rendering, восстановление кнопки,
  переход в диалог и оба testbench endpoint используют исходный nonblank ID.
- Новый локальный exact helper соответствует R27/R29: null/undefined/blank → '',
  прочий opaque text сохраняется. Literal null/NULL не являются отсутствующим ID.
  Helper не резолвит owner, не проверяет business access и не меняет schema.
- HTML escaping и URL encoding сохраняются. Message text, review query keys,
  filter/search/policy normalization, CSRF и автообновление не изменены.

## Baseline и доказательства

- Fresh fetch: HEAD=origin/main=`a31280daf87c96d1de1a79289a50406c428b5d9a`,
  parent `bbc6a8ff0270023d7865f790f7559745f3228c1c`.
  Commit R32 проверен по SHA/message/parent и фактическим 19 файлам.
  Root исходно чистый; blobs ai-ops.js и task details pinned.
- В detached worktree сначала добавлен новый Node test. Baseline: 32 cases,
  21 pass и 11 assertion failures; production fix после RED, тест и assertions
  не менялись. После fix: 32/32 GREEN; existing R27/R29: 18/18 GREEN.
- Harness исполняет полный IIFE с локальными DOM/fetch/timer doubles:
  точные rendered data attributes и navigation, reclassify/retrieve-debug URLs,
  percent/slash/HTML/Unicode chars, padded/plain и literal-null ID, blank/no-POST,
  error recovery, approve/reject buttons, CSRF meta/cookie и text normalization.
  Проверены оба JS syntax. Настоящий browser/HTTP/LLM/transport не запускались;
  HTML entity round-trip моделируется, browser E2E/manual acceptance не заявляется.
- Repository Maven wrapper:
  `.\mvnw.cmd '-Dtest=DialogAiOpsControllerWebMvcTest,MultibusinessFeatureFlagsTest,LegacyTicketIdSupportTest' test`.
  56/56 tests, три класса, failures/errors/skipped=0.
  AI Ops WebMvc 39, disabled-by-default flags 2, exact helper 15.
  WebMvc delegates mocked; выбранные tests не используют рабочую БД.
- Guard v1 до source apply ошибочно считал indentation-substring в двух
  row anchors; остановился RED без production mutation. V2 использует exact
  full-line anchors. Baseline и тестовые assertions не ослаблялись.
- Sass build породил status-only EOL changes в четырёх CSS; normalized content
  и git diff подтверждают отсутствие CSS patch. Они не переносятся в root.
- Guarded перенос: sandbox tests/diff/exact file set, затем fresh fetch,
  clean root/exact baseline/blob pins/new-file checks, те же patches,
  git diff --check и полное нормализованное content equality для всех 4 файлов.

## Границы и продолжение

- S2A source checkpoint, 01-278 🟡. S2B/R12 остаются
  PENDING/PROPOSED_NOT_APPROVED; owner не выводится из channel/user/label.
- Это не завершение business isolation. Следующий bounded review:
  incidents-workbench.js transport ticket inputs (три остающихся trim).
- Рабочая/production БД, aliases/assignment, migrations/backfill, flags,
  runtime/deployment не менялись. Staging, commit и push агент не выполнял.

## Изменённые файлы

- `spring-panel/src/main/resources/static/js/ai-ops.js`
- `spring-panel/src/test/js/ai-ops-exact-legacy-ticket-id-r33.test.js`
- `ai-context/tasks/task-details/01-278.md`
- `ai-context/changelog/2026-10-12_113812_01-278_ai_ops_ui_exact_ticket_identity.md`
