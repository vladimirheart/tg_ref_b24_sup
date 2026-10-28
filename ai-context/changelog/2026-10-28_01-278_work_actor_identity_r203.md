# 01-278 R203 — автор BUSINESS-статуса и категорий по ID сессии

Дата: 2026-10-28 (Europe/Moscow).
Baseline: 0845e8d482d32a909cea90140ec303d95ed3a3c5; parent 2ae45a0241903d656d297039bde7170c1d9443b1.

Промт пользователя:

> делай

## Изменение

После R189 session refresh и controller read прежний work writer выбирал actor
по lower(name). Его final actorId guard использовал ID из этого поиска. При
rename/delete + reused name перед writer или за время ожидания raw-ticket
advisory lock close/reopen/categories могли выполняться от другого аккаунта.
Смена регистра после locking SELECT также проходила финальные name checks.

Controller теперь передаёт Authentication; BusinessTicketWorkService.change
требует authenticated bound PanelUserPrincipal с positive ID/exact raw name/status.
Locking root SELECT, conditional UPDATE и post-write confirmation связывают
actor с теми же session ID/raw name. String-only API удалена. Несовпадение в
финальном UPDATE/confirmation даёт false с rollback root/categories/counters/
revision/audit/outbox. Audit ID/raw snapshot и resolved_by согласованы.

READ/OPERATE/ambiguity/status, full owner tuple, revision/status/category/channel
CAS, account/business/grant locks, REQUIRES_NEW/rollback, category limits/canonical
normalization, reopen categories, PAGE_DIALOGS/CSRF/R189 и HTTP contract сохранены.
Нет schema/dependencies/UI/grants/policy changes. R201 assignment и R202 participants
не менялись; work find/recent, остальные reads/AI и GENERAL остаются отдельными
следующими границами. Полная atomic revocation всех in-flight SQL snapshots не
заявляется. Изменение не требует сохранения/переноса старых тестовых данных.

## Проверки

97 SQL + 12 HTTP = 109 новых frozen cases сначала выполнены до main changes:
52 assertion failures / 57 controls / 0 errors/skips. С 213 прежними HTTP
controls baseline v1: 322 tests / 52 failures / 0 errors. Reflection bridge
только в новом SQL test позволяет одним source bytes проверить прежнюю String
и новую Authentication API; отдельный case запрещает String-only overload.
Product name lookup fallback отсутствует.

Final v2 regression + v4 identity: 952 tests / 11 suites GREEN, failures/errors/skips=0. HTTP
225 (213 прежних + 12 новых), SQL identity 97, existing work SQL, category
projections, work reads/MVC, legacy lifecycle, R189 session, R201 assignment/
R202 participant actor SQL и ScopedPrincipal включены. 15 неизменённых BUSINESS
Node scripts GREEN. В v2 единственный отказ — ошибка нового API-shape assertion: types.contains(String)
ошибочно запрещал также String status/action. Исправлена только проверка типа
identity argument по index 1; предыдущие source/JSON/reports сохранены. Новая SQL
версия заморожена, в v3 повторно проверена на baseline main: 97/49/0. Исходный
HTTP RED 225/3/0 относится к неизменному HTTP block; aggregate RED 322/52/0.
V4 identity: 97 GREEN. 10 GREEN regression XML v2 сохранены; source и class SHA
двух main + трёх fixtures совпали точно с пересобранными v4. Эти же inputs/
bytecode обосновывают reuse прежних regression results без повторения 150-case
work suite. SQL bytes v3→v4 и HTTP block v1→v2 не менялись. Три старых
fixture sources при inverse разрешённых operations точно совпали с baseline;
assertions/case names не ослаблены. Source/class/report SHA и freshness проверены.

Для close/reopen/categories проверены wrong/missing/zero/negative ID, exact name/
case/padding, rename/delete + replacement, unbound/anonymous/unauthenticated/
disabled principal, literal Unicode/padded/numeric names и BIGINT IDs >2^53/
Long.MAX_VALUE, current status/READ/OPERATE/ambiguity/business activity. 6 real
advisory wait cases подтвердили wait через pg_stat_activity перед rename/delete
и replacement. 6 same-transaction JDBC-boundary cases меняют case имени перед
final UPDATE или confirmation после audit/outbox: весь root/audit/outbox и
инъекция откатились. Это не симуляция unlocked concurrent update уже locked
account. 12 HTTP cases меняют account после настоящего session refresh/controller
read; при отказе root/revision/audit/outbox и соседние назначения/участники не
меняются. Existing SQL rollback/real lock/CAS/limits/reopen/grant/legacy checks
сохранены. Positive tests проверяют raw audit, resolved_by, counters, categories
и полный owner tuple outbox.

Backend — owned native PostgreSQL 16.15, loopback 54167, fresh schemas в
.tmp/r200-pg/data. Explicit ignored R200 JDBC supplier bytecode adapter сохранён;
tracked Testcontainers helper не менялся, Testcontainers не выполнялся. Docker
Desktop не запускался. Exact data/PID/port/listen/status startup guards прошли;
native server остановлен после тестов, evidence сохранено. R202 cache проверен
по all Java source и attested class SHA; 2 main + 3 fixtures собраны заново,
новый SQL test пересобран на revised RED v3 из тех же bytes. Все 83 migration sources неизменны;
V81/Flyway выполнялись только в test schemas.

## Среда и продолжение

Source/test: C:\Users\sushi\Git_H\tg_ref_b24_sup. Working project:
C:\Users\SinicinVV\git_h\tg_ref_b24_sup — другая машина/сессия.
Полные R184/R198 runtime blocks, R198 web/worker/V83/backup/rollback и исторический
R200 Docker incident сохранены. Remote probe/Compose/build/deploy/restart/working
migration не выполнялись. Последний подтверждённый runtime R198; R199–R203 rollout
не подтверждён. Для применения нужна новая сборка panel-web; новых migrations
нет, повтор V83 не требуется. Git permission не объявляется deploy approval.

Штатная проверка с Docker/Testcontainers на другой машине:

    cd spring-panel
    .\mvnw.cmd "-Dtest=BusinessTicketAssignmentHttpPostgresTest,BusinessWorkActorIdentityPostgresTest,BusinessTicketWorkPostgresTest,BusinessCategoryProjectionPostgresTest,BusinessTicketWorkReadPostgresTest,BusinessTicketWorkReadControllerWebMvcTest,LegacyLifecycleBoundaryPostgresTest,SessionAuthorityWebMvcTest,BusinessAssignmentActorIdentityPostgresTest,BusinessParticipantActorIdentityPostgresTest,ScopedPrincipalIdentityPostgresTest" test

Далее: оставшиеся name-based reads/AI, scoped children/media/feedback, GENERAL,
policy/AI/fleet и первый production S6. Historical S2B исключён R190, task 🟡.
Scope: 2 main + 3 existing tests + 1 new SQL test + task/S0/new changelog = 9 paths.
Commit/push разрешены до конца 2026-10-28 Europe/Moscow с expiry guard;
source apply отделён от Git publisher и не выполняет deploy/working migration.
