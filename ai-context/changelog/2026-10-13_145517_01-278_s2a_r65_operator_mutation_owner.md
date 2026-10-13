# 01-278 / S2A / R65 — exact operator-message до edit/delete

Время: 2026-10-13 14:55:17 MSK.
Baseline: `ac51d758fe68d10cc654df7066dd2d19d02c3c0b`.

Промт пользователя: «дальше».

Пользовательский R64 подтверждён: HEAD=origin/main, parent
`8a2d160cc1f0d79d7264dd6ba41bd92779affc28`, message/7 changed files проверены;
checkout чистый. Удалённое описание R52 не восстанавливается.

Изменённые файлы (6 project files):

- `spring-panel/src/main/java/com/example/panel/service/DialogReplyTargetService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogReplyService.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogReplyServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogOperatorMutationOwnershipPostgresqlTest.java`
- `ai-context/tasks/task-details/01-278.md`
- `ai-context/changelog/2026-10-13_145517_01-278_s2a_r65_operator_mutation_owner.md`

Фактический scope:

- Новый read-only hasOperatorMessageOwner переиспользует unique raw ticket и
  exact canonical target gate R64, затем требует ровно одну history row по
  ticket_id/user_id/channel_id/tg_message_id и sender='operator'. Null message ID,
  unowned/missing/ambiguous history и DataAccessException отклоняются.
  ID не trim-ится/не декодируется; это не business permission или strict flag.
- Edit/delete проверяют message owner до loadChannel, Telegram mutation, ledger,
  responsibility и existing local UPDATE, включая web-form branch.
  Deny возвращает прежнее «Сообщение оператора не найдено.». Existing error/
  success result fields и valid flow сохраняются.
- Existing UPDATE SQL/args/tuple filters, original_message COALESCE, edited_at/
  deleted_at behavior не менялись. Direct BotRuntime writers и sendReply/
  sendMediaReply R64 не переписывались. Четыре existing valid edit/delete
  fixtures получили только exact owner stubs, без wildcard grants.

Detached validation:

- Baseline новых 72 tests: 44 failures / 4 errors / 0 skipped.
  Все 48 negative cases RED, все 24 valid GREEN. Прежний код выбирал channel/
  вызывал provider до проверки history; missing history schema давала ошибку
  уже в UPDATE. Это подтверждённый sequencing defect, не fixture compile failure.
- Final Maven: 657/657 GREEN в 26 classes, 0 failures/errors/skipped.
  Fresh XML totals прочитаны и суммированы отдельно, все reports текущего run.
  Полные 643/443 main/test sources компилируются. Все 585 regressions R64 включены.
- 48 negative PG cases: wrong history user/channel/ticket/provider ID,
  non-operator sender, duplicate history/ticket, forged target user/channel,
  changed canonical channel, owner lookup failure и missing history schema
  × edit/delete × provider/web-form. Stale target подставляется controlled spy;
  canonical checks и history lookup используют real JDBC. При отказе нет
  transport (включая loadChannel), ledger, responsibility, metadata и mark UPDATE;
  полный snapshot history, включая сохранённую missing-schema таблицу, неизменен.
- 24 positive PG cases: raw padded/slash/reserved/Unicode/percent-looking/
  null-literal IDs × edit/delete × provider/web-form. Проверены actual UPDATE,
  result fields, original/edited/deleted state, attachment/message_type/reply_to,
  прежние exact provider/ledger/responsibility args. Foreign tuple и ai_agent
  с тем же provider ID, trimmed padded sibling сохраняются без изменения.
  Web-form остаётся local без Telegram mutation/ledger.
- New tests используют isolated Testcontainers PostgreSQL и настоящие target/
  history services/JDBC; Telegram, ledger, responsibility и metadata mocked.
  Real provider/network/browser/S3/production/business-scope E2E не заявляется.
  Existing HTTP tests — regressions прежних contracts.
- Десять unchanged JS files R27/R29/R33/R48/R50/R52/R53/R54/R55/R56 GREEN.
  Source invariants: baseline ReplyService восстановлен удалением двух blocks,
  TargetService — одного нового method, fixture — четырёх exact owner stubs.
  Sass CSS совпадает с baseline после EOL normalization и не входит в patch.

Sandbox GREEN и source apply разделены. Fresh HEAD/origin/status/blob guards,
exact six-file scope, normalized sandbox/root parity и diff check проверяются
отдельно. API routes/DTO shape, UI/cache keys, schema, permissions, flags,
attachment I/O, working DB не менялись. Migration/backfill, deploy, staging,
commit/push не выполнялись.

Ограничение: provider edit/delete и local UPDATE не стали атомарными; concurrent
change после preflight остаётся возможным, уже выполненную provider mutation
нельзя автоматически откатить. Новые locks/outbox/conditional UPDATE не добавлены.
TOCTOU/reconciliation, canonical public/composite linkage и storage namespaces —
отдельные scopes. S2B/R12 PROPOSED_NOT_APPROVED; 01-278 остаётся 🟡.
