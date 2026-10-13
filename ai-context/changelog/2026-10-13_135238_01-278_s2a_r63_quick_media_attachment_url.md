# 01-278 / S2A / R63 — exact attachment URL после quick media reply

Время: 2026-10-13 13:52:38 MSK.
Baseline: `193d8a42e70278b4b7b0899418fe2dcbb1dab4b7`.

Промт пользователя: «продолжи».

Пользовательский R62 подтверждён: HEAD=origin/main, parent
`c8690e7a02b39902ae377ce01b667ba6cd2e79ca`, message/8 changed files проверены,
checkout чистый. Удалённое описание R52 не восстанавливается.

Изменённые файлы (8 project files):

- `spring-panel/src/main/java/com/example/panel/storage/AttachmentStorageKeyResolver.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogConversationReadService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogQuickActionService.java`
- `spring-panel/src/test/java/com/example/panel/storage/AttachmentLegacyDialogReferenceResolverTest.java`
- `spring-panel/src/test/java/com/example/panel/controller/AttachmentTicketQueryHttpIntegrationTest.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogQuickMediaAttachmentUrlTest.java`
- `ai-context/tasks/task-details/01-278.md`
- `ai-context/changelog/2026-10-13_135238_01-278_s2a_r63_quick_media_attachment_url.md`

Фактический scope:

- AttachmentStorageKeyResolver предоставляет pure buildTicketAttachmentDownloadUrl:
  ordinary ticket ID и filename — path с single encoding; opaque ID, nested/
  backslash или literal percent filename — R62 query. Raw identity/filename не
  trim-ятся и не декодируются. Query сохраняет literal '+' через %2B и literal
  percent через %25. Missing/blank input не создаёт locator. Builder не проверяет
  owner/permission и не выдаёт storage/business grant: это остаётся в download.
- QuickActionService success response использует builder вместо raw concatenation.
  Receipt retain/rollback, cached multipart bytes, transport args/result fields,
  processing/reply registration, notifications и refresh flow не переписываются.
  Семь полей response сохраняются, изменяется только attachment URL.
- Direct-reference branch истории использует тот же builder. Metadata-key/path
  branches, SQL/args, owner gates и message/reply fields не меняются. Ordinary
  legacy URL для обычного имени сохраняется; filename с literal percent переводится
  в query также для plain ticket ID, чтобы не зависеть от запрета %25 в path.

Detached validation:

- Новый quick-service baseline: 35 tests / 34 expected failures / 0 errors.
  Только plain ID/plain filename уже выдавал нужный URL. Broken raw URLs для
  padded/slash/backslash/Unicode/reserved/percent/null-literal и nested filename
  воспроизведены до production change.
- Первый общий run: 483 tests / 1 failure / 0 errors. Это real HTTP defect,
  не ослабленное assertion: percent-looking filename с ordinary ID шёл через
  encoded-percent path и давал 401 вместо 200. Default StrictHttpFirewall отдельно
  проверен: raw path с %25 отвергается, query с тем же encoded filename — нет.
  Builder selector дополнен literal percent; security settings не меняются.
- Final Maven: 485/485 GREEN в 20 classes, 0 skipped. Fresh XML reports прочитаны
  отдельно: 485 tests, 0 failures/errors/skipped. Полные 643/441 main/test sources
  компилируются; включены все 435 targeted regressions R62.
- 35 новых quick-service cases проверяют raw exact IDs/filenames, single URL
  decode, отсутствующий fragment, все response fields, cached file bytes,
  transport args, retain/no rollback, AI registration и notification args.
  12 pure builder cases проверяют conventional path, opaque query, percent-looking
  filename и отсутствие locator при missing context.
- Два новых embedded HTTP cases выполняют настоящий quick upload и retain через
  AttachmentService/JDBC/local_fs, controlled mocked send создаёт metadata claim
  в isolated schema. Immediate returned URL отдаёт те же real temp-file bytes и
  206/Content-Range. Ordinary и opaque IDs проверены. При later foreign metadata
  claim URL даёт 404 без binary I/O; исходные file bytes остаются. Дополнительный
  firewall case проверяет existing default, не меняет production security.
- Это real local upload/download с controlled transport substitute, не live
  Telegram/VK/MAX, full quick POST/browser, S3 или production/business-scope E2E.
- Десять неизменённых JS files R27/R29/R33/R48/R50/R52/R53/R54/R55/R56 повторно
  GREEN на final source; diff check GREEN. Generated Sass CSS нормализованно
  совпадает с root, только Windows EOL status, не patch.
- Source invariants: QuickActionService совпадает с baseline после обратной
  замены одного import/URL call; history — после обратной замены direct builder;
  resolver existing methods неизменны. AttachmentService/object storage/owner
  SQL/controller целиком неизменны.

Validation отделена от guarded source apply. Fresh HEAD/origin_main/blob/status
guards, exact eight-file scope и normalized sandbox/root parity проверяются.
API routes/DTO shape, upload/receipt/rollback/streaming, schema/рабочая БД,
UI/cache keys, permissions и business flags не менялись. Migration/backfill,
deploy, staging, commit/push не выполнялись.

Canonical public/composite linkage, storage namespaces, outbound transport/
persistence preflight и cross-process claims/reconciliation остаются отдельными
scopes. S2B/R12 PROPOSED_NOT_APPROVED; 01-278 остаётся 🟡.
