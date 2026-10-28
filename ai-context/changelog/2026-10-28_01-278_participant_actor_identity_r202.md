# 01-278 R202 — автор изменений BUSINESS-участников по ID сессии

Дата: 2026-10-28 (Europe/Moscow).
Baseline: 2ae45a0241903d656d297039bde7170c1d9443b1; parent c2e2ad3443159e97845a6a3fc543fc5eb5d6967b.

Промт пользователя:

> продолжи

## Проблема и изменение

После R189 refresh и успешного controller read writer add/remove повторно
выбирал actor по логину. Rename/delete + reused name, в том числе за время
ожидания raw-ticket advisory lock, могли передать операцию другому аккаунту.
Controller теперь передаёт Authentication; service требует bound authenticated
PanelUserPrincipal с positive ID/exact raw username/status. Locking SELECT,
revision check, member lookup и финальные conditional INSERT/DELETE связывают
actor с теми же ID/raw name; String-only API удалена. Audit ID/snapshot согласованы.

Сохранены PAGE_DIALOGS/CSRF/R189, READ/OPERATE, ambiguity/status, owner tuple,
revision/CAS, roster 200, locks/REQUIRES_NEW, rollback/outbox и HTTP contract.
Нельзя добавить member без READ; удаление disabled/revoked/deleted member по
stored ID/snapshot остаётся разрешено оператору. Assignment writer R201, SQL
readers, work/AI, GENERAL и legacy participants не менялись. Нет новой policy,
grants, schema/dependencies или UI. Это не гарантия atomic revocation всех
in-flight SQL snapshots.

## Проверки

Frozen SQL 66 + HTTP 8 = 74 новых cases сначала выполнены против прежнего main:
33 assertion failures / 41 controls / 0 errors/skips. Вместе с 205 прежними HTTP
controls baseline v1: 279 tests / 33 failures / 0 errors. Test-only reflection
bridge позволяет тем же SQL bytes проверить старую String и новую Auth API;
отдельный case запрещает String-only overload. Product lookup fallback отсутствует.

Final v2: 619 tests / 14 suites GREEN, failures/errors/skips=0. HTTP
213, ScopedPrincipal 19, новые SQL 66, assignment actor R201, current/history
R199, participant reads/MVC, legacy participant write/audit и session R189.
15 неизменённых BUSINESS Node scripts GREEN. Frozen new SQL bytes/HTTP method
block не менялись. Три existing test fixture sources точно восстановлены до
baseline после inverse fixture operations; ни один старый assertion не ослаблен.
Source/class/report hashes/freshness и old/new test case names сопоставлены.

Проверены ID substitution/missing/zero/negative, exact name/case/padding, rename/
delete с replacement, unbound/anonymous/unauthenticated/disabled principal,
literal Unicode/padded/numeric names, BIGINT >2^53/Long.MAX_VALUE, grants/status
и removal неактивных members. 4 real advisory wait cases подтвердили ожидание
через pg_stat_activity и отказ после rename/delete + replacement, для add/remove.
2 same-transaction JDBC-boundary cases проверили final SQL повтор actor proof;
это не имитация unlocked concurrent update уже заблокированной строки. 8 HTTP
cases меняют account после настоящего session refresh/controller read; новых
roster/audit/outbox/revision effects при отказе нет. Existing CAS/rollback/roster
limits/member eligibility/security/legacy boundaries включены в regression.

Тестовый backend: owned native PostgreSQL 16.15, loopback 54167, fresh schemas
в .tmp/r200-pg/data; explicit ignored JDBC supplier bytecode adapter R200.
Tracked Testcontainers helper не менялся, Testcontainers не выполнялся. Docker
Desktop не запускался. Startup проверяет pg_ctl status, exact data/PID/port/listen;
ошибочный ExitCode guard R201 заменён до старта. R201 cache прошёл все Java source
и attested class SHA guards; 2 main + 3 existing tests перекомпилированы, новый
SQL test собран на RED из тех же bytes. Все 83 migration sources неизменны;
V79/Flyway использовались только в test schemas. Native server остановлен после
проверки, artifacts сохранены.

## Среда и продолжение

Source/test: C:\Users\sushi\Git_H\tg_ref_b24_sup. Рабочий экземпляр:
C:\Users\SinicinVV\git_h\tg_ref_b24_sup — другая машина/сессия.
Полные R184/R198 runtime и R198 web/worker/V83/backup/rollback blocks сохранены.
R200 local Docker incident не скрывается; нового Docker/Compose/remote probe/
build/restart/deploy/working migration не было. Последний подтверждённый runtime
R198; R199–R202 rollout не подтверждён. Нужен новый panel-web image; migrations
не добавлены и повтор V83 не требуется. Git permission не означает deploy approval.

Штатная проверка с Docker/Testcontainers на другой машине:

    cd spring-panel
    .\mvnw.cmd "-Dtest=BusinessTicketAssignmentHttpPostgresTest,BusinessParticipantActorIdentityPostgresTest,ScopedPrincipalIdentityPostgresTest,BusinessAssignmentActorIdentityPostgresTest,BusinessTicketCurrentAssignmentReadPostgresTest,BusinessTicketCurrentAssignmentReadControllerWebMvcTest,BusinessTicketCurrentAssignmentIdentityWebMvcTest,BusinessTicketAssignmentReadPostgresTest,BusinessTicketAssignmentReadControllerWebMvcTest,SessionAuthorityWebMvcTest,BusinessTicketParticipantReadPostgresTest,BusinessTicketParticipantReadControllerWebMvcTest,LegacyParticipantWriteBoundaryPostgresTest,LegacyParticipantAuditBoundaryPostgresTest" test

Дальше: оставшиеся name-based reads/work/AI, scoped children/media/feedback,
GENERAL, policy/AI/fleet и первый production S6. Historical S2B исключён R190;
общая задача остаётся 🟡. Scope: 2 main + 3 existing tests + 1 new SQL test +
task/S0/new changelog = 9 paths. Commit/push разрешены до конца 2026-10-28
Europe/Moscow с expiry guard; apply не выполняет Git/deploy/working migrations.
