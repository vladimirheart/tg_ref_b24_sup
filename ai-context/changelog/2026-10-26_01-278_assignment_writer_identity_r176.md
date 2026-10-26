# 01-278 — точные identity в конечных writers назначения, R176

Дата: 2026-10-26 (Europe/Moscow).

Промт пользователя:

> давай дельше

## Baseline и среда

Baseline R176 (2026-10-26): HEAD/origin/main/actual remote main
1ce9308fe4040cdc4de2b6f25dbbb19aed771e7b; parent
babbce504813de57ffe36745dc9a2749b907fa95. Последний commit задачи 01-281
«fix(incidents): исключить простой курсоров и разделить проверки #ai» проверен:
24 файла, 823 additions/104 deletions. R175 остаётся ancestor main; после fetch
сохранены два новых monitoring commits, fast-forward с 91b49ede1 без конфликтов.
Основной checkout/index исходно чистые; detached sandbox .tmp/01-278-r176.
Рабочий экземпляр и Docker находятся на другой машине/в другой сессии;
здесь source и только изолированные PostgreSQL Testcontainers. Рабочие revision,
DB/config/flags/providers неизвестны и не менялись; restart/rebuild/deploy/
migration/backfill рабочего экземпляра не выполнялись.
Суточный Git grant от 2026-10-23 истёк 2026-10-23 21:00 UTC. Новый grant не дан:
R176 source apply заканчивается без staging/commit/push; deployment=false.

## Изменение

R176: два финальных auto-assignment writers сохраняют точный account key
и непустой actor без trim, при прежнем case-insensitive SQL lookup:
DialogResponsibilityService.assignScopedResponsibleIfMissing и
ScopedSlaAssignmentService.assign. Имя с leading/trailing/both spaces, tab или
newline не может воспользоваться OPERATE похожего обрезанного account. Реальный
literal account с собственным current OPERATE сохраняется без подмены в
ticket_responsibles и SLA audit; loadScopedResponsible возвращает точное
сохранённое имя существующего/concurrent ответственного. Blank target/actor
по-прежнему обрабатывается прежними fallback правилами; canonical ai_agent
исключён, прежние length limits SLA применяются к полному значению.
Не изменены SQL grant/owner predicates, enabled/blocked/unique identity, active
business, membership/ALL_BUSINESSES роли, advisory/root locking, CAS revision,
атомарный SLA event и typed pre-strict UNRESOLVED compatibility. Legacy bare-ID
assignment/loadResponsible/mark-as-read используют прежние отдельные пути.
Source minimum V81; новых migrations, flags, UI/JS или grants нет.

R176 закрывает именно конечные writers, а не всю цепочку назначения. Upstream
ChannelTransportService assignment config, ChannelAssignmentRoutingService,
DialogParticipantService directory, ScopedAssignmentCandidateService eligibility
(в том числе username=btrim(username)) и SlaEscalationAutoAssignService config/
skills/queues/previous identity ещё требуют согласованного exact-key прохода.
Уже обрезанное upstream имя final writer восстановить не может. Сохранены gates
UI CSV/picker round-trip, ChannelApiController CRUD authority/business policy,
incident user targets, legacy attribution, global/system/task/PermissionService,
S2B и рабочий rollout/acceptance; общий статус 01-278 остаётся 🟡.

## Проверки

Проверки R176: 451 Java tests / 9 свежих XML GREEN,
failures/errors/skips=0: 8 GREEN suites v2 (.tmp/r176-panel-v2.log) и полный
повтор ScopedSlaAssignmentPostgresTest v3 (.tmp/r176-panel-v3.log), финальный
Maven v3 exit 0. Source/class/XML сохранённых suites совпадают с финальными.
До исправления оба
production source были exact baseline blobs: новый v1 воспроизвёл 10/10 failures
(два writers × пять whitespace variants), errors/skips=0. Original XML/log/test
SHA и production blobs сохранены в ignored .tmp/r176-baseline-report.xml,
r176-panel-v1.log и r176-baseline-red.json. Diagnostic source SHA неизменен
в финальном GREEN; отрицательные assertions не ослаблены.
Новый suite использует полную Flyway V81 PostgreSQL-схему, два похожих реальных
account с разными business grants, положительную собственную literal identity,
три OPERATE роли через membership/ALL_BUSINESSES, late disable/block/duplicate/
VIEWER/revoke/archive/system-only/foreign, frozen owner replacement/tuple/GENERAL/
raw collision, exact stored/concurrent read, actor fallback и SLA redirect/CAS.
Отказ сверяет полные root/assignment/audit snapshots; успех — exact target/actor,
previous responsible, revision и event. Existing SLA suite дополнительно проверяет
реальный frozen reader, rollback audit failure, revision ABA и гонку workers.
В старом DialogScopedResponsibilityPostgresTest положительные grants/fallback
используют canonical mixed-case target без добавленного padding; stored и
concurrent return assertions теперь сохраняют уже существовавший literal padding.
Их DB/grant/root assertions сохранены; новые tests отдельно проверяют отсутствие
trim alias. V2 выявил один прежний upgrade comparator: V76→latest после V77
учитывал только responsibility_revision, но V81 уже добавляет два root поля.
Исходный test blob и неизменность migration chain подтверждены; failure/XML/source
сохранены в ignored r176-sla-upgrade-v2-report.xml, r176-sla-upgrade-v2.java и
r176-fix-v3.json. V3 добавляет явные assertions business_work_revision=0 и
business_categories=[], сохраняя exact comparison всех прежних root/assignment
fields, revision tracking и отсутствие SLA audit при legacy writes. Миграции
не менялись, assertions не удалялись, triggers/constraints не отключались.
Весь SLA suite (26 tests) повторён целиком. Остальные выбранные suites неизменны. Source/class/XML freshness,
exact changed-file set и generated CSS baseline blobs проверены. Node/browser=0
(UI/JS неизменны). Проверки не являются live provider/Redis/proxy/fleet или
функциональной приёмкой рабочего экземпляра на другой машине.

## Файлы и передача

- ai-context/changelog/2026-10-26_01-278_assignment_writer_identity_r176.md
- ai-context/rules/03-multibusiness-s0-contract.md
- ai-context/tasks/task-details/01-278.md
- spring-panel/src/main/java/com/example/panel/service/DialogResponsibilityService.java
- spring-panel/src/main/java/com/example/panel/service/ScopedSlaAssignmentService.java
- spring-panel/src/test/java/com/example/panel/service/DialogScopedResponsibilityPostgresTest.java
- spring-panel/src/test/java/com/example/panel/service/ScopedAssignmentWriterIdentityPostgresTest.java
- spring-panel/src/test/java/com/example/panel/service/ScopedSlaAssignmentPostgresTest.java

Guarded source apply переносит ровно 8 файлов после GREEN, повторно проверяет
exact baseline/content/files/actual remote и завершается без Git publication.
Фактические состояния — ignored .tmp/r176-result.json и итоговый ответ.
Разрешение на Git publication требует нового пользовательского действия;
исторический grant от 23 октября не используется. Deployment=false.
