# 01-278 R213 — внутреннее управление AI по ID сессии

Время: 2026-10-28T16:05:30.404Z (UTC), Europe/Moscow.
Baseline 3a62a044e903b9f4a0c18279efcd49c52f5bb62e, parent 229e77e63679ff26e35bf4d3e881dce1489b3537.
HEAD/origin/main/actual remote совпали; R212 subject/11 paths/content проверены.

Промт пользователя:

> продолжи по задаче

## Изменение

Non-CAS setControl ScopedDialogAiPersistenceService принимал только имя автора.
API теперь принимает Authentication и существующий boundControlActor guard.
Positive ID/nonblank raw name/principal status проверяются до JDBC; SQL JOIN связывает actor
одновременно с ID и username сессии. При rename/delete/reuse/case change старый
principal не выбирает другую учётную запись. updated_by_user_id/updated_by сохраняют
ID/raw имя, без lowercasing audit name. String-only overload удалён.

Сохранены current READ/OPERATE/unique enabled/non-blocked account/active business/
grants, полный owner tuple и SAME_EXCLUDED_OWNER в conflict UPDATE, collision/
GENERAL/unresolved denial. Reason trimming/null/cap500, flags и fail-closed при SQL
failure прежние. Primitive не создаёт state/event; нового audit event не добавлено.
Гарантия statement-time, не global revoke после начала SQL snapshot.

У метода нет production callers. Исправлена внутренняя API-граница, новый HTTP
workflow не создан. Controller использует setControlExpected: весь timestamp-CAS,
atomic event rollback, recordHandoff, четыре reader API и helpers совпали с baseline
точно. Controller/workbench/MEMORY/source/handoff/UI/schema/dependencies/policy/
grants вне scope. Четыре прежние fixtures/22 call sites адаптированы к Authentication;
inverse transformations восстанавливают их точно, старые assertions не менялись.

Scope9 files:1 main,4 existing fixtures,1 new SQL test,task/S0/changelog.

## Проверки

Frozen99 SQL scenarios на неизменённом product code:99 tests/30 assertion
failures/0 errors,69 controls. Те же source bytes/case names GREEN. Final230
tests /5 selected SQL suites +15 BUSINESS JS GREEN, failures/errors/skips0:
new99,persistence58,workbench47,ScopedPrincipal19,source retrieval proof6+1.
Два Source methods выбраны потому, что их helper disableAi вызывает изменённый
primitive. Остальной source suite в R213 не запускался. HTTP не запускались:
нового route/consumer нет, controller и UI CAS unchanged; старые HTTP evidence
остаются историческими, за новый run не выдаются.

Отдельно проверены INSERT/conflict UPDATE с wrong/missing/nonpositive/stale IDs,
exact/raw names, invalid auth, BIGINT, current status/uniqueness/business/grants,
owner/collision, отказ overwrite старого owner, raw audit, constraint rollback,
reason trim/null/cap и zero-JDBC denial. Использован неизменный R208 schema/seed/
snapshot helper с V62; новая схема/support fixture не вводилась.
1345 Java cache inputs R212 проверены; compile1 new test, final1 main+4 adapted
fixtures. Source/class/report SHA/freshness, frozen names и неизменность остальных
методов проверены. Все source changes сначала прошли detached worktree validation.

Owned native PostgreSQL16.15, 127.0.0.1:54167, fresh isolated schemas, explicit
ignored R200 JDBC supplier с SHA/version/exact data-directory guard. Tracked
Testcontainers helper неизменен; Docker Desktop/Testcontainers не запускались.
JIT off только own test process; reused helper ANALYZE. 83 migration sources
неизменны, V62..64 только test schemas. Flyway и working migrations не запускались.
Test server остановлен; baseline/green reports/log/operators сохранены.
Это selected validation, не весь repository или working/provider acceptance.

Штатный повтор в изолированном test runner с Docker/Testcontainers:

    cd spring-panel
    .\mvnw.cmd "-Dtest=AiControlPrimitiveIdentityPostgresTest,ScopedAiPersistencePostgresTest,ScopedDialogAiWorkbenchPostgresTest,ScopedPrincipalIdentityPostgresTest,ScopedAiSourcePostgresTest#finalCandidateProofRejectsChangesAfterRetrievalAtTheActualSqlBoundary+candidateProofChecksPayloadAndOriginRatherThanTrustingAnIdAndRevision" test

## Среда и продолжение

Source/test: C:\Users\sushi\Git_H\tg_ref_b24_sup. Рабочий экземпляр на другой
машине/сессии: C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Полные R184/R198/R204/R207
runtime/rollback/V83 backup blocks сохранены. Последний документированный runtime
R207: webR206/workerR198/bot-runner прежний/foundation off. Deploy/restart/working
migrations/flags/provider calls из source-сессии не выполнялись. Для применения
source нужен новый panel-web image; новых migrations нет, V83 не повторять.

Прямой ScopedAiSourceService.createMemory(String), source/catalog/configure/
retrieval/handoff identity, child/media/feedback/GENERAL, policy/privacy/delivery/
fleet/flags/S6 остаются открыты. Task🟡; historical S2B снят R190. Source apply отделён
от Git publisher/deploy; commit/push разрешены до конца2026-10-28 Europe/Moscow,
с expiry guard. Старые changelogs не переписывались.
