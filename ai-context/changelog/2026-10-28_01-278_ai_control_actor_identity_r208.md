# 01-278 R208 — автор UI AI control по ID сессии

Дата: 2026-10-28 (Europe/Moscow). Baseline a8ac417ce79aebac0e6f67d2bfc13ad428511558, parent c2ee116f3147297a28b20165a8f1ac0a2a19aa86.

Промт пользователя:

> репо обновлён. проверь и продолжай по задача

## Изменение

Проверены R204–R207: work history, audits/roster и девять workbench reads уже
переведены на ID. R207 подтверждает web-only rollout R206 на рабочей машине;
worker R198, V83 ранее применена, flags не включались. Эти факты сохранены.

Следующее окно находилось в UI AI control: после R189 refresh/owner lookup
setControlExpected повторно выбирал actor по lower(name). Controller передаёт
Authentication; обе SQL ветки INSERT и timestamp-CAS UPDATE теперь требуют
positive bound principal ID и exact raw name/status. updated_by сохраняет raw
actor.username. String identity overload этой API удалён. Reused login/case change
не меняют автора; SQL одновременно проверяет current READ/OPERATE/uniqueness/
status/active business/full owner tuple. Отсутствующий control не upsert-ится при
устаревшей форме, update сверяет timestamp; ошибка event откатывает control/stamp.

PAGE_DIALOGS/CSRF/flags, reason limits, response/redirect, recordHandoff/worker
behavior сохранены. Non-CAS setControl(String), AI reads/workbench/memory/source/
handoff String paths остаются открытыми. Новых grants/global/manual/system policy,
UI/schema/dependencies нет. Гарантия statement-time, не atomic global revocation
после начала SQL snapshot.

## Проверки

234 tests / 6 selected suites GREEN, failures/errors/skips=0; 15 unchanged
BUSINESS Node scripts GREEN. Новый shared fixture, 61 SQL и 17 real-login HTTP
cases; existing workbench/persistence, ScopedPrincipal19 и session32 включены.
SQL baseline v2:61/29/0; HTTP baseline v3:17/6/0, aggregate78/35/0 (controls43).
Production/SQL/support inputs не менялись между RED phases. Те же final frozen
source bytes GREEN. Source/class/report SHA/freshness, compiled API/branches и
case names проверены. Inverse transforms трёх прежних test fixtures точно
восстановили baseline; их assertions не менялись. Test-only reflection bridge
проверяет прежнюю String и новую Authentication API; identity parameter index1
явно проверен, остальных String parameters это не касается.

V1 new HTTP ошибочно посылал padded login к unpadded stored name:17 failures.
V2 исправил только login; CSRF postprocessor затем менял repository и16 cases
не получали cookie. V3 использует actual post-login cookie/token roundtrip без
postprocessor, assertions сохранены; повторён baseline. Старые версии и XML/
logs/SHAs сохранены. Positive writes/strict raw audit/BIGINT, invalid principals,
current grants/status, both branches, stale forms и atomic event rollback покрыты.

Отдельный existing debt подтверждён до main changes на исходном HEAD: прежний
ScopedDialogAiHttpPostgresTest#renderedFormControlCasAndCuratedDraftRoundtrip
дал1 failure/0 errors (GET expected200, actual302/login). user(String) fixture
не bound по ID и конфликтует с R189. Полный legacy AI HTTP suite не заявляется
GREEN. В старом файле изменён только тип spy identity matcher для компиляции;
assertions сохранены. Нужна отдельная адаптация fixture, не ослабление R189.
Evidence: .tmp/r208-baseline-v2-ScopedDialogAiHttpPostgresTest.xml.

Test backend: owned native PostgreSQL16.15, loopback54167, fresh schemas в
.tmp/r200-pg/data. Explicit ignored R200 JDBC supplier bytecode adapter, tracked
helper без изменений; Testcontainers не выполнялся. JIT off только в этом own
test process, новые relations ANALYZE. Docker Desktop не запускался; startup
проверяет exact data/PID/port/listen/status, native server остановлен после tests.
1306 unchanged Java inputs cache R203 проверены, 27 changed R204–R206 inputs и3
новых files собраны свежими (10 main/20 tests); после правки2 main/3 fixtures
перекомпилированы. Все83 migration sources неизменны; V62/Flyway только test schemas.

## Среда и продолжение

Source/test C:\Users\sushi\Git_H\tg_ref_b24_sup; working project находится на другой
машине/сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Полные R184/R198/R204/R207
runtime/rollback/V83 backup checkpoints сохранены. Runtime остаётся webR206/R207,
workerR198/bot-runner прежние. Remote probes/build/Compose/up/restart/deploy/
working migration/flags/provider writes здесь не выполнялись. Для включения R208
нужен новый panel-web image; migrations нет, повтор V83 не требуется.

Штатный isolated Docker/Testcontainers runner на другой машине:

    cd spring-panel
    .\mvnw.cmd "-Dtest=AiControlActorIdentityPostgresTest,AiControlActorIdentityWebMvcTest,ScopedDialogAiWorkbenchPostgresTest,ScopedPrincipalIdentityPostgresTest,ScopedAiPersistencePostgresTest,SessionAuthorityWebMvcTest" test

Это focused suite, не claim GREEN всей старой AI HTTP fixture или live workflow.
Далее: legacy HTTP test fixture, AI non-CAS/reads/memory/source/handoff identity,
child/media/feedback/GENERAL, explicit policy/AI privacy/delivery/fleet/S6 gates.
Historical S2B исключён R190; task остаётся🟡. Source scope2 main+3 old fixtures+
3 new test/support+task/S0/changelog=11 paths. Source apply отделён от Git publisher;
commit/push разрешены до конца 2026-10-28 Europe/Moscow, deploy отсюда не выполнялся.
