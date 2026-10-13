# 01-278 / S2A / R59 — безопасное удаление и rollback новой загрузки

Время: 2026-10-13 11:29:25 MSK.
Baseline: `770abd80c08ac8e0a0ef18168dcc1974cd2a95b4`.

Промт пользователя: «продолжи».

Пользовательский R58 commit подтверждён: HEAD=origin/main, parent
`5741d3c14cba7efbb1447ebeff3f0d40d22f45d2`, message/8 changed files
проверены, checkout чистый. Удалённое описание R52 не восстанавливается.

Изменённые файлы и области (8 project files):

- `spring-panel/src/main/java/com/example/panel/storage/AttachmentService.java` —
  обычный delete требует unique ticket и matching exact physical-key owner.
  Upload проверяет safety reference до multipart stream/storage write.
  Новая prepareTicketAttachment сохраняет прежний store/metadata/layout и
  возвращает внутренний receipt с private/final actual implementation.
  Callback захватывает именно newly created file; retain/rollback synchronized,
  terminal и одноразовые. Receipt rollback не принимает string key/owner.
- `spring-panel/src/main/java/com/example/panel/storage/TicketAttachmentOwnerJdbcGuard.java` —
  shared direct-reference safety helper и отдельный read-only claim check только
  для service-created receipt: unique исходный ticket, отсутствие любого metadata
  key claim и history attachment claim по raw/normalized filename, key или
  relative attachments path. Foreign/deleted/partial claims и schema/lookup
  failure дают deny. Existing resolveUniqueOwner SQL не переписан.
- `spring-panel/src/main/java/com/example/panel/service/DialogQuickActionService.java` —
  media action получает receipt, делает rollback при failed result/RuntimeException
  и retain до success post-processing. Cleanup IOException/RuntimeException
  логируются, не заменяя исходный transport result/exception.
  Payload, cached multipart bytes, transport arguments, permissions,
  notification/audit pathways сохраняются.
- `spring-panel/src/test/java/com/example/panel/service/DialogQuickActionServiceTest.java` —
  три existing media fixtures используют receipt mock; прежние response/body/
  cached-byte/notification assertions сохранены. Error case требует rollback
  без retain, success/cache cases подтверждают retain без rollback.
- `spring-panel/src/test/java/com/example/panel/storage/AttachmentDeletionOwnershipBoundaryTest.java` —
  14 cases: unique ticket без owner proof, unsafe directory/filename, traversal,
  alias и duplicate ticket. Denied cases требуют no storage/JDBC interaction.
- `spring-panel/src/test/java/com/example/panel/storage/AttachmentUploadRollbackPostgresqlTest.java` —
  47 cases с реальными PostgreSQL и local_fs temp files: ordinary owner deletion,
  unique plain legacy fallback, unrelated file preservation, raw padded/slash/
  percent-looking/Unicode/null-literal identity, pending/retained/repeated/
  concurrent receipt, metadata/history/partial/foreign/deleted claims, schema/
  duplicate lookup failure, unsafe upload preflight. Шесть quick-action branches
  используют настоящие AttachmentService/storage/guard и mocked transport:
  success/error/runtime/cleanup IO/cleanup runtime/partial history.
  Ошибка отправки сохраняет исходный instance; serialized upload metadata
  содержит прежние originalName/storedName/mimeType/size/uploadedAt.
- `ai-context/tasks/task-details/01-278.md` — checkpoint R59 и ограничения;
  этот отдельный append-only changelog-файл.

Detached validation:
- RED baseline: 14 deletion tests / 13 expected failures / 0 errors — старый
  метод вызывал delete по одному ticket ID, в том числе при traversal/alias.
- В процессе validation исправлены test wiring к public production constructor
  и новое ошибочное JSON expectation createdAt → фактическое unchanged uploadedAt.
  Receipt contract вынесен в interface с private/final implementation:
  существующий subclass Mockito не mock-ит final classes; global mock-maker
  не менялся, actual receipt constructor/callback остаются private.
- Финальный Maven run: 268/268 GREEN в 15 classes, 0 skipped — новые delete (14),
  PG/file/receipt (47), quick service (32) и все 175 R58 regressions: descriptors,
  history, owner guard, MIME/Range, MVC, legacy uniqueness, metadata exact ID,
  R56 query MVC и existing embedded HTTP. Компилируются полные 643/436 main/test files.
- Десять неизменённых JS test files R27/R29/R33/R48/R50/R52/R53/R54/R55/R56 GREEN;
  diff check GREEN.
- Source invariants: download/descriptor/existence/open/streaming bodies,
  resolveUniqueOwner SQL и upload metadata record не менялись; прежний store body
  получает только safety preflight. UUID naming, физический storage layout и
  object-storage implementation не переписаны.

Новые tests — service/DB/temp-file evidence. Transport mocked: внешнему клиенту
ничего не отправлялось. Existing HTTP tests — регрессия query/controller contracts,
не HTTP E2E нового receipt/ownership path. Business grants, canonical assignment
и strict flags не включаются. Ordinary string-based delete не использует receipt
exception; доверенный cleanup вызывается только через созданный backend receipt.

Fail-closed cleanup может сохранить orphan при DB/storage failure. Receipt живёт
только в памяти текущего request; cross-process reconciliation и atomicity между
claim check и внешними concurrent DB writers не заявляются. Canonical public/
composite linkage, storage namespaces и DB-supplied history metadata fast paths
остаются дальнейшими отдельными scopes, S2B/R12 PROPOSED_NOT_APPROVED.

Source apply отделён от validation, защищён fresh HEAD/origin_main/blob/status
guard, exact changed-file set и normalized parity. Generated Sass CSS имеет
только Windows EOL status и не входит в patch. Рабочая БД, migration/backfill,
UI/cache keys, feature flags, deploy, staging, commit/push не менялись.
01-278 остаётся 🟡.
