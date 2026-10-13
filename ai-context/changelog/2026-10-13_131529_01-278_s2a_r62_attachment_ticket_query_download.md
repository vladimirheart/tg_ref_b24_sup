# 01-278 / S2A / R62 — query download вложений для exact opaque ID

Время: 2026-10-13 13:15:29 MSK.
Baseline: `c8690e7a02b39902ae377ce01b667ba6cd2e79ca`.

Промт пользователя: «продолжи».

Пользовательский R61 подтверждён: HEAD=origin/main, parent
`b9ba5eb4e9b0c3b26bf6e577ff806a7f9010277d`, message/9 changed files проверены,
checkout чистый. Удалённое описание R52 не восстанавливается.

Изменённые файлы (8 project files):

- `spring-panel/src/main/java/com/example/panel/controller/AttachmentController.java`
- `spring-panel/src/main/java/com/example/panel/service/DialogConversationReadService.java`
- `spring-panel/src/main/java/com/example/panel/storage/AttachmentStorageKeyResolver.java`
- `spring-panel/src/test/java/com/example/panel/service/DialogConversationReadServiceTest.java`
- `spring-panel/src/test/java/com/example/panel/storage/AttachmentLegacyDialogReferenceResolverTest.java`
- `spring-panel/src/test/java/com/example/panel/controller/AttachmentTicketQueryHttpIntegrationTest.java`
- `ai-context/tasks/task-details/01-278.md`
- `ai-context/changelog/2026-10-13_131529_01-278_s2a_r62_attachment_ticket_query_download.md`

Фактические изменения:

- AttachmentController добавляет GET /tickets/by-ticket?ticketId=...&filename=...
  с required params. MultiValueMap проверяет, что keys ровно ticketId/filename
  и каждый передан один раз: duplicate/unknown request даёт 400 до service,
  missing param — прежний global 400. Decoded raw values и Range передаются в
  существующий downloadTicketAttachment без normalization/additional decoding.
  Новый endpoint не обходит PAGE_DIALOGS, unique ticket или exact-owner guard.
- History генерирует query URL для opaque ID вне [A-Za-z0-9._-]+ либо filename
  со slash/backslash. Regex только выбирает транспорт; owner identity не меняется.
  Ordinary IDs сохраняют старый path URL. Metadata-key/path URLs, DTO shape,
  SQL/query args и history message/reply fields не переписываются.
- Pure legacy resolver принимает новый by-ticket envelope: ровно два параметра
  с exact names, любой порядок, single servlet decoding (+ → space), raw opaque
  ticket ID не trim-ится. Missing/blank/duplicate/unknown/malformed params и
  fragments fail closed. Existing R61 probe/descriptor связываются с этим же ID.

Detached validation:

- Baseline нового HTTP class: 20 tests / 18 failures / 0 errors. Route отсутствовал;
  NoResourceFoundException попадал в существующий generic handler (500), а
  ordinary legacy path и anonymous request уже проходили свои assertions.
- Первый production-source run: 72 tests / 1 failure / 0 errors — assumption
  нового fixture об exact Content-Type без charset. Embedded HTTP добавляет
  charset=UTF-8 к video/mp4; проверяется MIME compatibility, бинарные bytes,
  length и Range assertions сохранены. Source streaming не менялся.
- Один generated test patch был отклонён до записи из-за empty hunk;
  исправленная V2 применена только в sandbox. Working root оставался чистым.
- Final Maven: 435/435 GREEN в 19 classes, 0 skipped. Fresh XML reports прочитаны
  отдельно: 435 tests, 0 failures/errors/skipped. Полные 643/440 main/test sources
  компилируются. Все 385 targeted regressions R61 включены.
- Новый HTTP class: 26 cases через настоящий embedded Tomcat/socket и HTTP client.
  Шесть arbitrary-ID binding cases используют mocked response и проверяют raw
  arguments/range/auth. Actual media/ownership cases используют mock только как
  test wiring delegate в настоящий AttachmentService/JDBC/local_fs с temp files.
  History URL → повторное чтение query reference → download проверяет exact
  padded/slash/plus/percent-looking/Unicode ID, полные bytes, MIME, length,
  Content-Disposition и 206/Content-Range bytes. Nested filename проверен отдельно.
- Denied owner/sibling/conflicting metadata/duplicate ticket/unsafe filename/
  missing schema/blank/unknown context требуют 404 и no binary storage I/O.
  Missing/duplicate/unknown params — 400 до service, anonymous — 401 до service,
  missing PAGE_DIALOGS — 403 до storage. Ordinary unique legacy path остаётся 200.
  Старый encoded-slash path подтверждён как 400 до service в embedded Tomcat.
- Pure parser дополнен 20 cases, history URL — четырьмя. Existing padded URL
  expectation обновлён на query; exact descriptor call arguments сохранены.
  Десять неизменённых JS scripts R27/R29/R33/R48/R50/R52/R53/R54/R55/R56 GREEN.
  diff check GREEN; generated Sass CSS нормализованно совпадает с root (только EOL).

Validation отделена от source apply. Fresh HEAD/origin_main/blob/status guards,
exact eight-file scope и normalized sandbox/root parity проверяются отдельно.
AttachmentService, object storage, owner SQL, upload/delete/receipt/streaming,
schema/рабочая БД, UI/cache keys, DTO shape, permissions и business flags не менялись.
Migration/backfill, deploy, staging, commit/push не выполнялись.

Это controlled source/isolated PostgreSQL/local_fs/embedded HTTP evidence, не
production/browser/S3 или complete business-scope E2E. Новые query params не
являются business grant. Canonical public/composite linkage, storage namespaces,
quick-send URL generation и cross-process claims/reconciliation остаются
отдельными scopes. S2B/R12 PROPOSED_NOT_APPROVED; 01-278 остаётся 🟡.
