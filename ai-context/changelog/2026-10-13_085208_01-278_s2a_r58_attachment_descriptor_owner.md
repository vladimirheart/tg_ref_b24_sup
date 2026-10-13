# 01-278 / S2A / R58 — owner guard для descriptors и наличия вложения

Время: 2026-10-13 08:52:08 MSK.
Baseline: `5741d3c14cba7efbb1447ebeff3f0d40d22f45d2`.

Промт пользователя: «продолжай».

Пользовательский R57 commit подтверждён: HEAD=origin/main, parent
`96a593ef5b48cd5e16cf6674cb928db8b8e9bdcc`, message/8 changed files
проверены, checkout чистый. Удалённое описание R52 не восстанавливается.

Изменённые файлы и области (8 project files):

- `spring-panel/src/main/java/com/example/panel/storage/AttachmentService.java` —
  direct/by-path/by-storage-key descriptors проверяют owner до storage.
  Direct требует exact raw ticket ID; by-path использует тот же effective key,
  который затем открывается. Existence probe возвращает false до storage при
  invalid owner. Сохранены прежние Java entrypoints, UUID-prefix stripping,
  descriptor name/size, local-path fallback и try-with-resources closure.
  Новые ticket-bound overloads проверяют exact equality context/owner;
  null/blank context не включает unbound lookup. Generic File not found
  не сообщает, какая ownership check отказала.
- `spring-panel/src/main/java/com/example/panel/service/DialogConversationReadService.java` —
  ровно два файловых enrichment call sites передают исходный ticket ID в новые
  bound overloads. Остальной history source, metadata fast paths, URL/DTO,
  fallback на имя из history, metadata size и error handling не переписаны.
- `spring-panel/src/main/java/com/example/panel/storage/AttachmentStorageKeyResolver.java` —
  leading attachments root marker имеет приоритет перед embedded marker.
  Исправлен воспроизведённый путь attachments/channel/attachments/file:
  сохраняется channel/attachments/file вместо ошибочного file.
  Absolute legacy extraction, весь suffix и literal percent/plus сохраняются.
- `spring-panel/src/test/java/com/example/panel/storage/AttachmentStorageKeyResolverTest.java` —
  прежние пять assertions/cases сохранены; три relative/slash/mixed-case
  Windows path regressions и literal percent/plus check доводят count до 9.
- `spring-panel/src/test/java/com/example/panel/storage/AttachmentDescriptorOwnershipBoundaryTest.java` —
  25 cases: unknown owner, padded/traversal/dot/double-separator aliases,
  direct filename traversal, missing bound context, false при storage failure
  и close binary stream без чтения содержимого.
- `spring-panel/src/test/java/com/example/panel/storage/AttachmentDescriptorOwnershipPostgresqlTest.java` —
  29 cases на реальном PostgreSQL в изолированных схемах и local_fs temp files:
  exact metadata owner (включая padded/slash/reserved/plus/Unicode),
  namespace/sibling collisions, foreign context, metadata/history mismatch,
  multiple owners, invalid/deleted metadata, duplicate/absent ticket,
  missing schema/file, unique legacy fallback, сохранение original name/size.
  Реальная связка history reader → bound descriptor → guard → storage проверяет
  три положительных и три отрицательных enrichment branches; denied cases
  используют verifyNoInteractions(storage).
- `ai-context/tasks/task-details/01-278.md` — checkpoint R58 и ограничения;
  этот отдельный append-only changelog-файл.

Detached validation:
- Descriptor boundary baseline — 18 tests / 18 expected failures / 0 errors.
  Старый код отдавал descriptor либо exists=true, assertion не ослаблялся.
- Первый общий run — 120 tests / 1 failure: relative root/nested attachments
  path терял namespace. Отдельный resolver baseline — 9 tests / 3 expected
  failures. Исправлен production resolver, positive history assertion сохранён.
- Итоговый Maven run — 175/175 GREEN в 12 classes: boundary (25), новые PG/file
  tests (29), history (14), resolver (9), R57 owner guard (41), download boundary
  (7), MIME/Range (4), media MVC (2), legacy uniqueness (2), metadata exact ID (3),
  R56 query MVC (34), existing embedded HTTP (5). Полные main/test sources
  компилируются (643/434 files).
- Десять неизменённых JS regression files R27/R29/R33/R48/R50/R52/R53/R54/R55/R56
  GREEN после финального code patch; diff check GREEN.
- Source invariants: upload/delete/download/open/streaming method bodies
  сохранены; history source полностью совпадает после обратной замены только
  двух bound call sites.

Новые tests являются service/DB/local-file evidence, не browser/production/S3
E2E. Existing embedded HTTP tests остаются регрессией ранее добавленных query
endpoints, не доказательством нового descriptor HTTP path. Проверка identity
не является business grant: системные permissions и S2B/S3 gates не включаются.
DB-supplied history metadata/URL fast paths, writer/delete ownership и canonical
public/composite linkage остаются отдельными дальнейшими scopes.

Source apply отделён от validation и защищён fresh HEAD/origin_main/blob/status
guard, exact changed-file set и normalized root/sandbox parity. Generated Sass
CSS имеет только Windows EOL status и не входит в patch. Рабочая БД,
migration/backfill, storage layout, API/UI/cache keys, feature flags, deploy,
staging, commit/push не менялись. S2B/R12 PROPOSED_NOT_APPROVED, 01-278 — 🟡.
