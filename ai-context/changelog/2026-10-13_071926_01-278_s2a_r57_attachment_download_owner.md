# 01-278 / S2A / R57 — точный владелец файла перед скачиванием

Время: 2026-10-13 07:19:26 MSK.
Baseline: `96a593ef5b48cd5e16cf6674cb928db8b8e9bdcc`.

Промт пользователя: «даальше».

Пользовательский R56 commit подтверждён: HEAD=origin/main, parent
`c4fbe9615e2ba793eb69288054920699d04aff19`, message/16 changed files
проверены, checkout чистый. Удалённое описание R52 не восстанавливается.

Изменённые файлы и области (8 project files):

- `spring-panel/src/main/java/com/example/panel/storage/TicketAttachmentOwnerJdbcGuard.java` —
  новый PostgreSQL read-only owner lookup: metadata/history должны совпадать
  по exact nonblank legacy ID, один owner group и unique ticket. Несколько
  сообщений одного владельца допустимы; любая invalid/deleted reference
  в группе закрывает доступ. Пустая/конфликтная metadata не включает fallback.
  При полном отсутствии metadata допускается только прежний первый каталог
  с одним ticket-кандидатом среди всех slash-prefixes и uniqueness check.
  Небезопасные пути/alias, nested prefix collision, duplicate/unknown owner и
  DataAccessException дают deny. Storage reference нормализуется отдельно;
  raw ticket ID не trim-ится и percent-looking ID не декодируется.
- `spring-panel/src/main/java/com/example/panel/storage/AttachmentService.java` —
  direct download сверяет exact requested ticket с владельцем physical key;
  by-storage-key использует logical key, by-path — тот же attachments suffix,
  который открывает storage. PAGE_DIALOGS проверяется первым, invalid owner
  возвращает bodyless 404 до чтения storage. Streaming/MIME/Range не переписаны.
- `spring-panel/src/test/java/com/example/panel/storage/AttachmentDownloadOwnershipBoundaryTest.java` —
  шесть RED→GREEN случаев padded directory, traversal, dot/double separator
  и direct filename traversal; дополнительный deny без PAGE_DIALOGS
  подтверждает отсутствие JDBC/ticket/storage calls.
- `spring-panel/src/test/java/com/example/panel/storage/TicketAttachmentOwnerJdbcGuardTest.java` —
  41 case: 27 используют реальный PostgreSQL в изолированных схемах, 13
  unsafe-key cases отсекаются до JDBC, один проверяет lookup failure.
  Exact padded/slash/reserved/plus/Unicode/null-literal/percent-looking owners,
  namespace/sibling collisions, history mismatch, invalid/deleted references,
  duplicates, legacy fallback и direct owner agreement проверяются отдельно.
- `spring-panel/src/test/java/com/example/panel/storage/AttachmentServiceMediaResponseTest.java` —
  существующие четыре MIME/Range/metadata/duplicate regressions сохранены;
  fixtures дополнены owner guard dependency.
- `spring-panel/src/test/java/com/example/panel/controller/AttachmentControllerMediaWebMvcTest.java` —
  прежний photo/voice/video MVC scenario сохранён. Добавлен MockMvc scenario
  с реальными PostgreSQL, owner guard, AttachmentService и local_fs temp file:
  storage namespace содержит literal attachments segment, exact padded/slash
  owner читается как Range 206 и by-path 200; после metadata/history mismatch
  оба routes возвращают 404 с verifyNoInteractions(storage).
- `ai-context/tasks/task-details/01-278.md` — checkpoint R57 и ограничения;
  этот отдельный append-only changelog-файл.

Detached validation: baseline шести новых service cases — 6 expected failures
(200 вместо 404), 0 errors. Исправление не ослабляет 404/no-storage assertions.
Итоговые Java reports: 93/93 GREEN в семи классах — owner guard (41), download
boundary (7), media response (4), media MVC (2), LegacyTicketIdJdbcGuard (2),
metadata exact ID (3), R56 media/location query MVC (34). Maven компилирует
полные main/test sources; constructor call sites обновлены в рамках scope.

Десять неизменённых JS regression files GREEN:
R27/R29/R33/R48/R50/R52/R53/R54/R55/R56. Diff check GREEN.
Generated Sass CSS имеет только Windows EOL status и не входит в source patch.
Source apply отдельно защищён fresh HEAD/origin_main/blob/status guard и
нормализованной root/sandbox parity check; stage/commit/push не выполняются.

MockMvc + реальный PostgreSQL + local_fs temp file доказывает owner lookup и
streaming/controller interaction, но не embedded Tomcat/browser/production/S3
E2E. PAGE_DIALOGS остаётся legacy permission; эта проверка устанавливает
однозначную identity, не grant и не canonical business assignment. Без metadata
однозначный прежний ticket-directory fallback сохранён, opaque slash-ID не
угадывается. Descriptors, writer layout и canonical public/composite linkage
требуют отдельных дальнейших scopes.

Рабочая БД, migration/backfill, storage layout, API/UI/cache keys, feature flags,
deploy, staging, commit и push не менялись. S2B/R12 PROPOSED_NOT_APPROVED,
01-278 остаётся 🟡.
