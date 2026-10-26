# 01-278 — точная identity при выборе ответственного, R177

Дата: 2026-10-26 (Europe/Moscow).

Промт пользователя:

> хорошо. продолжай. разрешение на коммит и пуш продлеваю на текущие сутки

## Baseline и среда

Baseline R177 (2026-10-26): HEAD/origin/main/actual remote main
e10cd97066352feb3a7ba1925e0199fc1d3c2f5d, parent
1ce9308fe4040cdc4de2b6f25dbbb19aed771e7b. R176 уже опубликован пользователем:
проверены parent, subject, exact 8 paths и нормализованные SHA256 каждого файла
против r176-validation.json; повторная публикация R176 не выполнялась.
Основной checkout/index исходно чистые, detached sandbox .tmp/01-278-r177.
Рабочие проект и Docker находятся на другой машине/в другой сессии. Здесь
source/test checkout и только изолированные PostgreSQL Testcontainers; рабочие
revision/DB/config/flags/providers не проверялись и не менялись. Рабочие restart,
rebuild/deploy/migration/backfill не выполнялись.
Пользователь продлил commit/push на текущие сутки — 2026-10-26 Europe/Moscow,
expiry 2026-10-27 00:00 +03 (2026-10-26 21:00 UTC). Source apply и Git publisher
разделены; publisher проверяет срок, baseline, exact file/content set и remote.
Разрешение не включает рабочий rollout или приёмку.

## Изменение

R177 закрывает обрезку account key в активном выборе ответственного:
ChannelTransportService сохраняет scalar/list assignment selectors без trim,
с Locale.ROOT case folding и case dedup отдельно от названий отделов.
Notification arrays используют тот же exact-key normalizer, их контракт R175
сохранён. ChannelAssignmentRoutingService читает single/pool keys и каталоги
без trim; scoped и общий assignable catalog DialogParticipantService возвращают
реальные username. DialogOperatorOption сохраняет этот ключ в compact constructor;
displayName/avatar/department/role по-прежнему нормализуются как labels. Legacy participant CRUD,
profile lookup и их прежний normalizeIdentity остаются отдельными путями.
Assignable catalog introspection привязана через to_regclass к реально используемой
users relation; одноимённая users.role другой схемы больше не ломает каталог.
ScopedAssignmentCandidateService больше не исключает реальный literal username
только за внешние пробелы: blank/ai_agent, unique enabled/unblocked account,
current OPERATE membership/ALL_BUSINESSES, active business/channel, frozen owner
и exact workload predicates сохраняются. Похожий plain account не даёт прав.
SlaEscalationAutoAssignService сохраняет single/fallback/typed pool identity,
operator skills/queues map keys и previous responsible без trim; identity map
lookup использует Locale.ROOT. Прежние blank и unpadded null sentinel сохранены;
labels, rule matching, typed-list grammar и порядок pool не менялись.
Финальные writers R176, locks/CAS/atomic event, namespaces и long cursor semantics
не менялись; новых migrations, grants, flags, JS/UI и внешних вызовов нет.
SettingsDialogSlaAiConfigService уже копирует typed значения без нормализации,
его исходник не менялся; round-trip проверен через SharedConfigService.

R177 не закрывает UI CSV/picker round-trip, SLA policy preview/audit parser,
upstream SLA actor label, ChannelApiController CRUD authority/business policy,
incident user targets, legacy responsible/active/spans attribution,
global/system/task/PermissionService и S2B/рабочий rollout. Общий статус 🟡.

## Проверки

Проверки R177: 494 Java tests / 15 свежих XML GREEN,
failures/errors/skips=0, Maven v4 exit 0. Новый PostgreSQL suite:
71 tests, полная Flyway V81-схема, два похожих реальных account с разными
business grants; отрицательные selectors и успешные сохранение → каталог →
resolver → финальный writer, real DB channel questions_cfg save/reload и
typed SLA settings file save/reload. Проверены пять whitespace variants,
single/pool/fallback, три стратегии и department queue, exact workload/capacity,
skills/queues isolation, exact previous identity и SLA reassignment/CAS event,
current revoke/disabled/blocked/duplicate/VIEWER/archive/foreign/channel/owner.
На baseline production blobs новый diagnostic method дал 25/25 failures,
errors/skips=0 (пять путей × пять вариантов). XML/log/test SHA и production
blobs сохранены в ignored .tmp/r177-baseline-red.json,
r177-baseline-report.xml и r177-panel-v1.log. Исходный test source SHA сохранён вместе с XML; diagnostic method/aliases
и account/grant/root fixtures неизменны в финальном GREEN.
Первый полный v2 (455 tests) обнаружил 27 failures только нового suite:
дополнительный trim в DialogOperatorOption после SQL-чтения. Исходный XML и
диагностика сохранены в r177-routing-v2-report.xml/r177-fix-v3.json; baseline
DTO blob проверен. V3 исправляет DTO, повторяет все 13 suites и добавляет
DialogQuickActionServiceTest и DialogWorkspaceWorkflowSnapshotServiceTest как
потребителей DTO. V3: 494 tests, 6 failures/1 error только нового suite. Пять
failures выявили metadata union чужих schemas (u.role), что исправлено внутри
assignable catalog; явный foreign-schema decoy теперь есть в каждом catalog test.
Две новые fixture assumptions исправлены: immutable public ID меняется только
через delete/recreate root, а create writer без channel key получает результат
resolver при disabled channel. SLA active-channel и остальные прямые writer
negatives сохранены; triggers/constraints не отключались. Исходные source/XML
и три точечных test transformations сохранены в r177-routing-before-v4.java,
r177-routing-v3-report.xml и r177-fix-v4.json. Diagnostic method/aliases и
account/grant/root setup побайтово прежние; в v4 все 15 suites повторены целиком.
Две прежние scoped fixtures обновлены по контракту: single mixed-case ALICE
без добавленного padding и previousResponsible с исходными внешними пробелами;
grant/root/frozen assertions сохранены, новые negatives не ослаблены.
Выбранные regression suites: ScopedAssignmentWriterIdentityPostgresTest (55), ScopedAssignmentCandidatePostgresTest (43), ScopedAssignmentRoutingServiceTest (23), ScopedSlaRoutingServiceTest (19), ChannelAssignmentRoutingServiceTest (3), SlaEscalationAutoAssignServiceTest (4), DialogParticipantSnapshotPostgresqlTest (73), DialogParticipantLegacyTicketGuardTest (3), LegacyParticipantWriteBoundaryPostgresTest (51), ScopedSlaAssignmentPostgresTest (26), ChannelApiControllerWebMvcTest (50), ChannelNotificationIdentityPostgresTest (34), DialogQuickActionServiceTest (35), DialogWorkspaceWorkflowSnapshotServiceTest (4).
Проверены source/class/XML SHA/freshness, exact file set, diff --check и
generated CSS baseline blobs. Node/browser=0: JS/UI не менялись. Это source
проверки, не функциональная приёмка рабочего экземпляра на другой машине.

## Файлы и передача

- ai-context/changelog/2026-10-26_01-278_assignment_routing_identity_r177.md
- ai-context/rules/03-multibusiness-s0-contract.md
- ai-context/tasks/task-details/01-278.md
- spring-panel/src/main/java/com/example/panel/model/dialog/DialogOperatorOption.java
- spring-panel/src/main/java/com/example/panel/service/ChannelAssignmentRoutingService.java
- spring-panel/src/main/java/com/example/panel/service/ChannelTransportService.java
- spring-panel/src/main/java/com/example/panel/service/DialogParticipantService.java
- spring-panel/src/main/java/com/example/panel/service/ScopedAssignmentCandidateService.java
- spring-panel/src/main/java/com/example/panel/service/SlaEscalationAutoAssignService.java
- spring-panel/src/test/java/com/example/panel/service/ScopedAssignmentRoutingIdentityPostgresTest.java
- spring-panel/src/test/java/com/example/panel/service/ScopedAssignmentRoutingServiceTest.java
- spring-panel/src/test/java/com/example/panel/service/ScopedSlaRoutingServiceTest.java

Guarded source apply переносит ровно 12 файлов после GREEN и завершается без
staging/commit/push. Отдельный publisher использует текущий суточный grant.
Фактические состояния фиксируются в ignored .tmp/r177-result.json и итоговом
ответе; наличие этой записи само по себе не является доказательством push.
Deployment=false, общий статус 01-278 остаётся 🟡.
