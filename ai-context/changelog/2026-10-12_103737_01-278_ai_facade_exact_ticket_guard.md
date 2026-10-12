# 01-278: точный ticket ID до AI facade guard (R31)

Дата и время: 2026-10-12 10:37:37, Europe/Moscow.

Промт пользователя: `внесены дополнительные правки в рамках задачи. проанализируй и давай дальше`

## Review дополнительных правок

- Свежий `HEAD=origin/main=b5c3c5be7ef20ae3a16c7e67c34f0b207ca3a388`
  после fetch; parent `6846809ed0f804fb855fd46d164985003ae7d178`.
  Последний R29 проверен по SHA/parent/message и фактическим 8 файлам.
  Рабочее дерево исходно чистое. Учтены source checkpoints R21/R23/R25/R27/R29.
- Старый следующий шаг уже выполнен: parity mapper передаёт Instant
  в общий accumulator, timestamps вложенных top_reasons восстановлены.
- Bot clients/outbox и dialogs UI сохраняют точные nonblank ID; regression
  tests этих пакетов повторно выполнены без изменения пользовательского кода.
- Найдена следующая граница: AI facade использовал trim до SQL unique guard.
  Padded ID мог проверяться по соседнему plain ID до вызова AI delegates.

## Фактические изменения

- Ровно две строки `DialogAiAssistantService`: вместо trim используется
  проверка StringUtils.hasText с сохранением исходного ticket_id.
  Изменены общий uniqueTicketId gate и optional monitoring ticket filter.
- Missing/duplicate exact ID отклоняется без fallback на trimmed sibling.
  Nullable/blank filter global monitoring сохраняет прежнюю семантику.
  Text/event/actor normalization, public API и constructor не менялись.
- В existing facade guard test добавлены 15 случаев. Реальный PostgreSQL
  guard проверяет paired roots, unknown padded ID и duplicate exact ID
  рядом с unique plain sibling. Проверяются отсутствие вызовов защищённых
  delegates при отказе, exact forwarding, null-text ID и blank input.
  Facade boundary использует mocked downstream services — не persistence E2E.

## Проверки

- Detached worktree от exact baseline. Исходные blobs: facade
  `25f32721e5bd48020b285ba7be3791b3ccbd92f5`, existing test
  `8fb870e751658b45537a427dc5f192caef35bca8`, task
  `52c6d8b9c6598384bb5c9abd36e35f2fa55e89c2`.
- Baseline run: 16 facade tests, 9 failures, 0 errors/skipped.
  После source fix те же assertions/fixtures: `16/16` GREEN.
- Panel Maven wrapper: `97/97`, 8 классов, failures/errors/skipped = 0.
  AI Ops WebMvc: 39; message flow: 2; operator feedback: 3; facade guard: 16;
  state: 3; parity: 15; telemetry data: 17; shared JDBC guard: 2.
- Bot Maven wrapper с `-pl :bot-core -am`: `7/7` tests, 2 класса;
  Panel clients: 3, UI outbox: 4; failures/errors/skipped = 0.
- R27/R29 Node source-contracts: `18/18` (8 + 10), 12 JS syntax checks GREEN.
  Это детерминированные source-contracts, не browser/manual acceptance.
- Java compile и AI Ops WebMvc context GREEN. Полный PanelApplicationTests
  не запускался: wiring/schema не менялись. PostgreSQL только Testcontainers.
- Exact file-set и diff checks до переноса; fresh fetch/clean baseline guard
  перед переносом. В root применяются те же deterministic patches и
  проверяется LF/CRLF-normalized content parity каждого из четырёх файлов.

## Границы и следующий шаг

- AI state/monitoring/persistence и flows ещё содержат собственную
  нормализацию ticket ID. Они не исправлены этим facade-only пакетом;
  весь AI-контур и cross-business access не объявляются закрытыми.
- R12 task provenance и S2B решения остаются PROPOSED_NOT_APPROVED/PENDING;
  task 01-278 остаётся 🟡. Business owner не выводится из user/channel/label.
- Рабочая БД, aliases/assignment, migration/backfill, strict flags, queues,
  runtime/deployment, staging, commit и push агентом не изменялись/не выполнялись.

## Изменённые файлы

- `spring-panel/src/main/java/com/example/panel/service/DialogAiAssistantService.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogAiAssistantServiceLegacyTicketGuardTest.java`
- `ai-context/tasks/task-details/01-278.md`
- `ai-context/changelog/2026-10-12_103737_01-278_ai_facade_exact_ticket_guard.md`

