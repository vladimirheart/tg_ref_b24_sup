# 01-278 / S2A / R60 — проверка владельца metadata перед history DTO

Время: 2026-10-13 11:52:32 MSK.
Baseline: `b5dc97889468614ec523bd9a90fba34f662cb2fb`.

Промт пользователя: «продолжи».

Пользовательский R59 commit подтверждён: HEAD=origin/main, parent
`770abd80c08ac8e0a0ef18168dcc1974cd2a95b4`, message/8 changed files
проверены, checkout чистый. Удалённое описание R52 не восстанавливается.

Изменённые файлы и области (6 project files):

- `spring-panel/src/main/java/com/example/panel/service/DialogConversationReadService.java` —
  SQL выбирает внутренние presence/raw-owner/class/deleted markers metadata.
  До attachment URL и fast-path name/size проверяется exact raw owner (без
  trim-helper value), active dialog class, известный provider и, при non-null
  key, разрешённый exact storage owner. Invalid metadata не выдаётся за absent:
  rejected attachment имеет null URL/name/size/status/provider/note, включая
  history file_name fallback; storage/descriptor не вызывается. Message text,
  original text, sender, reply IDs/preview и history ordering сохраняются.
  Owned missing/external/unresolved states, absent-metadata compatibility и
  прежний SQL fallback без metadata join сохранены.
- `spring-panel/src/main/java/com/example/panel/storage/AttachmentService.java` —
  добавлен только hasTicketAttachmentOwnerByStorageKey: read-only exact-ID
  predicate через прежний guard, без binary storage и без новых grants.
  Upload/delete/receipt/download/descriptor/existence implementation не меняется.
- `spring-panel/src/test/java/com/example/panel/service/DialogAttachmentMetadataProjectionPostgresqlTest.java` —
  36 cases на реальном PostgreSQL в изолированных схемах: raw padded/slash/
  reserved/plus/Unicode/null-literal/percent-looking IDs, trimmed sibling/foreign/
  blank/null owner, wrong/unknown/deleted metadata, class/provider/key conflicts,
  multiple/shared references и owner lookup failure. JSON не содержит rejected
  URL/name/size; reply preview и message fields сохраняются. Valid metadata,
  missing/external/unresolved, absent-metadata name, same-owner shared key,
  missing requested context и S3-provider projection проверены отдельно.
  Positive fast paths и denied cases требуют verifyNoInteractions(storage).
- `spring-panel/src/test/java/com/example/panel/storage/AttachmentDescriptorOwnershipPostgresqlTest.java` —
  R58 negative history metadata-key case усилен: null URL/name/provider вместо
  старого raw-history filename fallback. Существующие null-size/no-storage
  assertions сохранены. Два branches без metadata сохраняют прежнее имя.
- `ai-context/tasks/task-details/01-278.md` — checkpoint R60 и ограничения;
  этот отдельный append-only changelog-файл.

Detached validation:
- Первый new-test run: 27 cases / 17 failures / 0 errors; одна failure —
  ошибочное предположение нового unresolved fixture об existing name fallback.
  Fixture уточнён под прежний contract: own history file_name, null attachment URL.
  Повторный source baseline: 27 cases / 16 expected failures / 0 errors.
  Negative assertions не ослаблены.
- После production guard: 70/70 GREEN — 27 новых projection cases, 14 history
  regressions и 29 R58 descriptor PG cases. Дополнены edge/provider/context/reply
  проверки; финальный run: 304/304 GREEN в 16 classes, 0 skipped — 36 новых
  projection cases и все 268 targeted R59 regressions.
- Компилируются полные 643/437 main/test sources. Десять неизменённых JS files
  R27/R29/R33/R48/R50/R52/R53/R54/R55/R56 и diff check GREEN.
- Source invariants: AttachmentService полностью совпадает после удаления
  нового read-only predicate; previous history/categories, query args/error
  fallback, URL/meta/notes/preview/value method bodies сохранены.

Это SQL/service/DTO evidence. S3-provider case проверяет projection без
соединения с object storage; browser/production/S3 E2E нового history gate не
заявляется. Existing embedded HTTP cases остаются query/controller regressions.
Metadata parent ID сравнивается с raw JDBC String: общий value() намеренно
продолжает trim-ить display text, но не используется для нового identity gate.

Read-only predicate не является business authorization и не включает strict
flags. Raw legacy attachment URLs без metadata и existing SQL fallback сохраняют
compatibility; полное canonical/public-composite linkage, storage namespaces,
cross-process claims/reconciliation и business-scope E2E остаются отдельными
дальнейшими scopes. S2B/R12 PROPOSED_NOT_APPROVED.

Source apply отделён от validation, защищён fresh HEAD/origin_main/blob/status
guard, exact changed-file set и normalized parity. Generated Sass CSS имеет
только Windows EOL status и не входит в patch. Рабочая БД, migration/backfill,
API routes/DTO shape, UI/cache keys, permissions, feature flags, deploy,
staging, commit/push не менялись. 01-278 остаётся 🟡.
