# 01-278 / S2A / R61 — exact-owner legacy attachment references в истории

Время: 2026-10-13 12:50:10 MSK.
Baseline: `b9ba5eb4e9b0c3b26bf6e577ff806a7f9010277d`.

Промт пользователя: «продолжай в рамках задачи».

Пользовательский R60 подтверждён: HEAD=origin/main, parent
`b5dc97889468614ec523bd9a90fba34f662cb2fb`, message/6 changed files проверены,
checkout чистый. Ранее удалённое описание R52 не восстанавливается.

Изменённые файлы (9 project files):

- `spring-panel/src/main/java/com/example/panel/storage/AttachmentStorageKeyResolver.java`
- `spring-panel/src/main/java/com/example/panel/storage/AttachmentService.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogConversationReadService.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogConversationReadServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/storage/AttachmentDescriptorOwnershipPostgresqlTest.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogLegacyAttachmentOwnershipPostgresqlTest.java`
- `spring-panel/src/test/java/com/example/panel/storage/AttachmentLegacyDialogReferenceResolverTest.java`
- `ai-context/tasks/task-details/01-278.md`
- `ai-context/changelog/2026-10-13_125010_01-278_s2a_r61_history_legacy_attachment_owner.md`

Фактический scope:

- History без per-row metadata требует exact-owner proof для local reference
  до формирования attachment URL/name/size. Rejected reference получает null
  URL/name/size/status/provider/note, включая history file_name; binary storage
  не вызывается. Own message/original text/reply IDs/preview/order сохраняются.
  SQL fallback без metadata join не обходит новый gate.
- Pure AttachmentStorageKeyResolver классифицирует filename/storage key/stored
  path и распознаёт только известные relative ticket API envelopes. Query
  декодируется один раз (URLDecoder, + → space), direct path segments —
  один раз (UriUtils, literal plus сохранён). Explicit opaque ticket ID
  сравнивается без trim. Malformed/duplicate/unknown params, fragments,
  unsupported API и protocol-relative references не угадываются как local file.
- AttachmentService добавляет read-only legacy owner probe и descriptor dispatch
  через прежние ticket-bound direct/key/path guards. Probe не открывает binary.
  Nested key и decoded API envelope описывают тот же locator, который выдаёт URL,
  а не дополнительный filename внутри ticket directory. Absolute attachments
  path превращается в by-path URL. Query builder кодирует literal '+' как %2B;
  metadata key проходит тот же encoding. Имя отсутствующего owned API binary
  берётся из classified locator, не из названия endpoint.
- External raw http(s) и own history filename без locator сохраняются, без
  external fetch и без локального storage grant. Metadata gate R60 и owned
  availability states остаются. Existing descriptor display quirks для literal
  percent/hash не меняются; decoding для owner/storage authority не повторяется.
- Fixtures existing history URL tests явно подтверждают только нужные owner
  references; отрицательные assertions не ослаблены. R58 history tests теперь
  требуют null URL/name/provider и для legacy direct/path rejected branches.

Detached validation:

- Первый новый PG baseline run: 33 tests / 29 failures / 0 errors. Все 15
  foreign/unsupported URL cases дали ожидаемый RED до storage; nested-key/API
  locator descriptor mismatch также воспроизведён. В ходе проверки исправлены
  assumptions двух display-name fixtures под existing quirks и duplicate-owner
  fixture: ambiguous parent должен дать empty history, а не одну redacted row.
- После guard: 112/112 GREEN в четырёх targeted classes. После дополнительных
  parser/direct/lookup/fallback cases: 381/381 GREEN; финальное дополнение
  metadata encoding/missing-binary names: 385/385 GREEN в 18 classes, 0 skipped.
  Финальные XML reports прочитаны отдельно: 385 tests, 0 failures/errors/skipped.
- 38 новых PostgreSQL/local_fs cases проверяют foreign/unsupported locators,
  JSON redaction/reply preservation, exact padded/slash opaque identity,
  namespace collision/duplicate/missing schema/lookup failure, actual file size,
  single decoding, missing-file names и metadata URL round-trip без binary I/O.
  37 pure parser cases проверяют literal percent/plus, servlet query/path semantics,
  opaque ID preservation и отказ malformed/unsupported envelopes.
  Пять новых service descriptor denial cases и negative SQL-fallback branch
  дополняют все 304 targeted regressions R60.
- Полные main/test sources компилируются: 643/439. Десять неизменённых JS scripts
  R27/R29/R33/R48/R50/R52/R53/R54/R55/R56 повторно GREEN на final source.
  diff check GREEN. Existing HTTP cases — transport regressions, не новый
  download/browser/business-scope/S3/production E2E.
- Source invariants: после удаления новых methods/imports AttachmentService и
  AttachmentStorageKeyResolver полностью совпадают с baseline. Upload/delete/
  receipt/download/existence implementations и owner SQL не меняются.
  Generated Sass CSS нормализованно равен root baseline (только EOL), не patch.

Validation отделена от guarded source apply. Fresh HEAD/origin_main/blob/status
guards, exact nine-file scope и normalized sandbox/root parity проверяются.
Schema/рабочая БД, migration/backfill, API routes/DTO shape, UI/cache keys,
permissions, business flags, deploy, staging, commit/push не менялись.

Это укрепление legacy compatibility boundary, не business authorization.
Canonical public/composite linkage, storage namespaces, encoded-slash download
HTTP contract, cross-process claims/reconciliation и business-scope E2E остаются
отдельными scopes. S2B/R12 PROPOSED_NOT_APPROVED; 01-278 остаётся 🟡.
