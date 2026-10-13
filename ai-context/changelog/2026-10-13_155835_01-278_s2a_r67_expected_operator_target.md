# 01-278 / S2A / R67 — исходный target tuple при operator edit/delete

Время: 2026-10-13 15:58:35 MSK.
Baseline: `11a21b8ef75faf4f79f21f483f16be70f440675a`.

Промт пользователя: «дальше».

Пользовательский R66 подтверждён: HEAD=origin/main, parent
`0b6e34917c238080fed8c960cfb7f4dc98572368`, message/5 changed files проверены;
checkout чистый. Удалённое описание R52 не восстанавливается.

Изменённые файлы (9 project files):

- `spring-panel/src/main/java/com/example/panel/service/DialogReplyTargetService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogReplyService.java`
- `spring-panel/src/main/java/com/example/panel/service/BotRuntimeTicketWriteService.java`
- `spring-panel/src/test/java/com/example/panel/service/BotRuntimeTicketWriteServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogReplyServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogOperatorMutationOwnershipPostgresqlTest.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogOperatorExpectedTargetPostgresqlTest.java`
- `ai-context/tasks/task-details/01-278.md`
- `ai-context/changelog/2026-10-13_155835_01-278_s2a_r67_expected_operator_target.md`

Фактический scope:

- Target-aware overloads operator edit/delete добавляют expected user/channel
  в WHERE через JDBC parameters. Explicit null target/user/channel даёт zero
  до guard/DB, без fallback. Private helpers сохраняют R66 base SQL, parent/
  history uniqueness и canonical EXISTS, SET/args/COALESCE/timestamp semantics.
  Legacy public signatures используют прежний SQL и argument order.
- ReplyService передаёт исходный preflight target во все четыре provider/local
  edit/delete calls. BotRuntime operator edit передаёт user/channel исходного
  TicketSnapshot. Поздний transfer не может перенаправить UPDATE на history
  другого tuple с тем же raw ticket/provider ID. No business grant/strict flag.
- Четыре existing valid stubs переведены на exact target-aware signature без
  wildcard grants; existing negative assertions полностью сохранены и дополнены
  двумя never assertions новых overloads. Другие send/runtime branches не менялись.

Detached validation:

- Baseline 98 tests в 2 classes / 30 failures / 0 errors/skipped.
  New PG suite: 48 cases / 24 transfer failures, все 24 valid GREEN.
  Runtime: 50 cases / 6 new transfer failures, existing 44 GREEN.
  Это actual history retarget, не fixture/schema/compile failure.
- Final Maven: 763/763 GREEN в 28 classes, 0 failures/errors/skipped.
  Все 704 regressions R66 и 59 новых cases включены; fresh XML totals после
  завершения процесса прочитаны отдельно. Полные 643/445 main/test sources
  компилируются. Новый suite содержит 48 real PG flows и 5 API unit cases;
  runtime suite дополнен шестью actual PG cases.
- 24 negative flows: user/channel/both transfer × edit/delete × provider/web
  × plain/padded ID. Foreign future-canonical operator history заранее существует
  с тем же provider ID. Initial real preflight проходит; transfer выполняется
  в mocked provider callback либо web-session hook, до writer SQL snapshot.
  Все history rows сохраняются относительно snapshot после controlled transfer;
  responsibility/metadata не вызываются. Existing error wording сохранён.
- 24 valid flows: raw padded/slash/reserved/Unicode/percent-looking/null-literal
  IDs × edit/delete × provider/web. Проверены real UPDATE, result fields,
  original/edited/deleted state, attachment/message_type/reply_to, прежние
  provider/ledger/responsibility args. Foreign operator/ai_agent и trimmed
  sibling не меняются. Web-form остаётся local.
- Шесть runtime cases меняют canonical tuple после получения target, но
  сохраняют ранее загруженный TicketSnapshot. Updated=false, exists=true;
  history, ticket_active/responsibles/participants, UI outbox и provider ledger
  не получают дополнительной mutation.
- Пять API unit cases проверяют exact raw ID/message/user/channel binding order,
  сохранение message whitespace, R66 base predicates и unresolved target deny
  без JDBC/guard/metadata calls. Они добавлены после production overloads;
  baseline reproduction относится к 98 cases выше.
- Isolated Testcontainers PostgreSQL/real JDBC/services; provider и orchestration
  ledger/responsibility/metadata controlled mocks. Provider attempt/ledger могут
  уже состояться: rollback этого side effect не заявляется. Interleavings
  sequential и committed до statement, не multi-thread stress/serializable proof,
  live Telegram/browser/production/S3 или complete business-scope E2E.
- Source invariants: base SQL text blocks равны R66; остальные TargetService
  methods неизменны после обратной замены wrapper/helper region. ReplyService
  равен baseline после обратной замены четырёх calls, BotRuntime — одного call.
  Old runtime fixture равен baseline после удаления нового method; old deny
  assertions сохранены, stubs меняют только signature/expected tuple.
- Transformation v1/v2 остановилась до production writes: anchor substring
  ошибочно считал разные indentation rows одинаковыми, затем hunks шли не по
  source order. V3 использует full-line unique anchors и source-ordered hunks;
  проверена на emitted Java/final tests. Root до GREEN оставался чистым.
- Десять unchanged JS files R27/R29/R33/R48/R50/R52/R53/R54/R55/R56 GREEN.
  Generated Sass CSS равен baseline после EOL normalization и исключён из patch.

Sandbox GREEN и source apply разделены. Fresh HEAD/origin/status/blob guards,
exact nine-file scope, normalized sandbox/root parity и diff check проверяются
отдельно. API routes/DTO/UI/cache, permissions, business flags, schema,
attachments, outgoing send и другие runtime operations не менялись.
Working DB, migration/backfill, deploy, staging, commit/push не выполнялись.

Граница: expected tuple связывает eligible history с исходным target в snapshot
UPDATE, но не блокирует concurrent changes после snapshot/последующие business
transfers и не делает provider + DB + side-effects атомарными. Legacy signatures
намеренно остаются current-canonical compatibility paths. Locking/outbox/
reconciliation, public/composite linkage, storage namespaces и business-scope
acceptance — отдельные scopes. S2B/R12 PROPOSED_NOT_APPROVED; 01-278 остаётся 🟡.
