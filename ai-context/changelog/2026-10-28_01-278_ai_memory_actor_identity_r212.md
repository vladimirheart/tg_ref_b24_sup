# 01-278 R212 — создание MEMORY по ID сессии

Время: 2026-10-28T15:33:11.260Z (UTC), Europe/Moscow.
Baseline 229e77e63679ff26e35bf4d3e881dce1489b3537, parent c448ce463b5fb31c89e38e72310a4decaa9065c9.
HEAD/origin/main/actual remote совпали; R211 subject/12 paths/content проверены.

Промт пользователя:

> продолжай

## Изменение

После controller resolve/CONFIGURE precheck имя автора могло быть переиспользовано
другой учётной записью. Workbench createMemory теперь получает Authentication и
проверяет bound principal существующим guard R210. Сам SQL INSERT связывает
actor.id с session ID и actor.username с точным raw именем. Source и audit сохраняют
этот numeric ID/raw username. Старый String-only overload удалён; controller
передаёт Authentication. Raw audit больше не lowercases имя автора.

Текущие unique enabled/non-blocked account, active business, READ и ADMIN CONFIGURE,
полный owner tuple и text-only evidence/fingerprints перепроверяются тем же SQL.
Обе page authorities, CSRF/feature flags, reviewed, DRAFT/revision1, title/body
trimming/limits, key idempotency и atomic source/audit/evidence rollback сохранены.
Поздний отказ возвращает409/no-store без частичного draft/audit/evidence.
Гарантия statement-time; revoke после начала SQL snapshot не заявляется.

Workbench reads/helpers и весь persistence/source/handoff services неизменны.
UI/schema/dependencies/policies не менялись; новых migrations нет.
Scope11 files:2 main,3 existing fixtures,3 new test/support files,task/S0/changelog.
7 old fixture call sites адаптированы к Authentication; inverse transformations
восстанавливают исходные fixtures точно, старые assertions не менялись.

## Проверки

Frozen51 SQL+25 real-login/CSRF HTTP на исходном product code:76 tests/
20 assertion failures/0 errors, 56 controls. Те же source bytes/case names
GREEN. Final228 tests /7 selected suites +15 BUSINESS JS GREEN, failures/errors/
skips0: new76, workbench47, ScopedPrincipal19, R209 HTTP35, R210 HTTP19, R189 session32.
SQL проверяет reused/missing/mismatched IDs/raw names, invalid auth, BIGINT, current
status/uniqueness/business/grants, owner/collision, raw source/audit identity,
evidence, idempotency и rollback обеих dependent inserts. HTTP mutations происходят
после R189 refresh/resolve и настоящего CONFIGURE precheck перед INSERT. Проверены
оба page rights/CSRF/anonymous/flags, ADMIN и ALL_ADMIN, exact stored raw audit,
повтор формы, late owner/grant/evidence и atomic rollback.

Первый setup v1:76 errors до assertions — inherited seed TRUNCATE businesses
не включал FK sources/events. V2 исправляет только seed: единый TRUNCATE всех
referencing test tables и ANALYZE fixture relations; schema/triggers и SQL/HTTP
assertions не менялись. V2 также выявил утечку собственных rollback-test CHECK в
следующий HTTP scenario: control повторной формы не мог создать первую MEMORY.
V3 seed удаляет только эти два test-only CHECK перед сценарием. Product constraints/
triggers не отключались. SQL/HTTP assertions и sources не менялись с v1; менялся
только support fixture. V1/v2 source/manifest/XML/log сохранены. Final manifest v3
зафиксирован до product edit. Baseline reports/log SHA сохранены, source/class/
report SHA/freshness и case names проверены. 1342 Java cache inputs R211 совпали;
compile3 new sources, затем1 support fixture в v2 и1 в v3; final2 main+3 adapted fixtures.

Owned native PostgreSQL16.15, 127.0.0.1:54167, fresh isolated schemas, explicit
ignored R200 JDBC supplier с SHA/version/exact data-directory guard. Tracked
Testcontainers helper неизменен; Docker Desktop и Testcontainers не запускались.
JIT off только own test process; fixture ANALYZE. 83 migration sources неизменны,
V62..64/Flyway только test schemas. Test server остановлен, artifacts сохранены.
Это selected validation, не весь repository или working/provider acceptance.

Штатный повтор в изолированном test runner с Docker/Testcontainers:

    cd spring-panel
    .\mvnw.cmd "-Dtest=AiMemoryActorIdentityPostgresTest,AiMemoryActorIdentityWebMvcTest,ScopedDialogAiWorkbenchPostgresTest,ScopedPrincipalIdentityPostgresTest,ScopedDialogAiHttpPostgresTest,AiWorkbenchReadIdentityWebMvcTest,SessionAuthorityWebMvcTest" test

## Среда и продолжение

Source/test: C:\Users\sushi\Git_H\tg_ref_b24_sup. Рабочий экземпляр на другой
машине/сессии: C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Полные R184/R198/R204/R207
runtime/rollback/V83 backup blocks сохранены. Последний документированный runtime
R207: webR206/workerR198/bot-runner прежний/foundation off. Deploy/restart/working
migrations/flags/provider calls из source-сессии не выполнялись. Для применения
R208/R210/R211/R212 нужен новый panel-web image; migrations нет, V83 не повторять.

Прямой ScopedAiSourceService.createMemory(String), non-CAS setControl(String),
source/catalog/configure/retrieval/handoff identity, child/media/feedback/GENERAL,
policy/privacy/delivery/fleet/flags/S6 остаются открыты. Task🟡; historical S2B снят
R190. Source apply отделён от Git publisher/deploy; commit/push разрешены до конца
2026-10-28 Europe/Moscow, с expiry guard. Старые changelogs не переписывались.
