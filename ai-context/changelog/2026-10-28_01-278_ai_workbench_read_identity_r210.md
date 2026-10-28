# 01-278 R210 — чтение AI-диалога по ID сессии

Дата: 2026-10-28T12:25:23.035Z (UTC), Europe/Moscow. Baseline 36646e41b51d47cc7170e9581fadffc72d8c1fbc, parent c20f9f7553d211f8e0051f223a60120cbdbd984a.
R209 subject/4 paths/content проверены, HEAD/origin/main/actual remote совпали.

Промт пользователя:

> давай дальше

## Изменение

Следующая связная граница — resolve/messages ScopedDialogAiWorkbenchService и их
controller calls. Authentication несёт positive bound principal ID/exact raw name;
authenticated/non-anonymous/status guard отказывает до SQL. Обе read query связывают
actor ID/raw name в том же statement, затем прежний READ проверяет current account,
uniqueness/business/grants. String-only overloads этих двух read APIs удалены.
После R189 refresh rename/delete/reuse/case change не выбирает reader по новому имени.

Canonical publicId, messages full expected owner tuple, collision/GENERAL/unresolved
denial, current text-only history/no attachments/no deleted/unlinked/AI records,
preview1000/fingerprint/descending/limit50 сохранены. PAGE_DIALOGS/KB/flags/private404/
no-store не менялись. Locator resolve может законно вернуть текущий canonical owner;
замороженный owner tuple — условие messages, не дополнительный контракт locator.
Statement-time гарантия: модель может содержать поля, законно прочитанные ранее;
atomic whole-page revocation или revoke после SQL snapshot не заявляется.

Тела createMemory и всего persistence service неизменны. Их String boundaries,
findControl/canOperate/state/events, non-CAS setControl, source/handoff APIs остаются
отдельными шагами. UI/policy/grants/schema/dependencies и trusted worker не менялись.

## Проверки

Frozen 80 SQL +19 real-login HTTP cases: на unchanged baseline99 tests/32 failures/
0 errors,67 controls. Те же final source bytes GREEN. Final 249 tests/
7 selected suites +15 unchanged BUSINESS JS GREEN, failures/errors/skips0.
Включены R20935, R20817, R18932, workbench47 и ScopedPrincipal19. Четыре прежние
fixtures адаптированы только к Authentication type; inverse transforms восстанавливают
baseline точно, assertions не менялись. Новые case names/SHAs в RED/GREEN совпали.

SQL: wrong/missing/nonpositive/stale ID/name, invalid authentication, raw literal
names/BIGINT, current rights/status/ambiguity/business/owner, text filters, preview/
fingerprint/order/cap. HTTP использует настоящий login/CSRF и MockHttpSession при
explicit store-type=none; mutations после R189 refresh перед выбранным SQL. resolve
отказывает private404; messages отдаёт пустой список и не отдаёт текст. Valid VIEWER/
ADMIN/ALL_VIEWER, PAGE_DIALOGS/KB/anonymous/flags проверены. Reflection bridge только
в новых tests позволяет одним source проверить прежнюю String и новую Auth API;
identity parameter index1 проверен явно, route publicId String не запрещается.

1336 unchanged cache inputs R209 проверены. Baseline compile3 новых test sources;
final compile2 main+4 adapted fixtures+frozen HTTP test с прежними bytes: его Mockito
verification descriptor требует перекомпиляции после API change. Source/class/report
SHA/freshness проверены. Тесты на owned native PostgreSQL16.15, loopback54167, fresh
schemas .tmp/r200-pg/data, explicit ignored R200 supplier с data/version guard;
tracked helper без изменений, Testcontainers не выполнялся. JIT off только own test
process; fixture relations ANALYZE. Docker Desktop не запускался; test server
остановлен, artifacts сохранены. 83 migrations неизменны, V62/Flyway только test schemas.
Это selected validation, не весь repository или working/provider acceptance.

Штатный повтор на изолированном Docker/Testcontainers runner:

    cd spring-panel
    .\mvnw.cmd "-Dtest=AiWorkbenchReadIdentityPostgresTest,AiWorkbenchReadIdentityWebMvcTest,ScopedDialogAiWorkbenchPostgresTest,ScopedPrincipalIdentityPostgresTest,ScopedDialogAiHttpPostgresTest,AiControlActorIdentityWebMvcTest,SessionAuthorityWebMvcTest" test

## Среда

Source/test C:\Users\sushi\Git_H\tg_ref_b24_sup. Рабочий экземпляр на другой машине/
сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup; runtime R207: webR206/workerR198,
bot-runner прежний, foundation off. Полные R184/R198/R204/R207 runtime/rollback/V83
backup blocks сохранены. Remote/build/restart/deploy/working migrations/flags/provider
calls здесь не выполнялись. Применение product R208/R210 требует нового panel-web
image; новых migrations нет, V83 не повторять. Исторические changelogs не менялись.

Scope12 paths:2 main,4 old fixtures,3 new tests/support,task/S0/этот changelog.
Task остаётся🟡; прочие AI identity, child/media/feedback/GENERAL, policy/privacy/
delivery/fleet/flags/S6 gates открыты. Historical S2B снят R190. Source apply отделён
от Git publisher/deploy; commit/push разрешены до конца2026-10-28 Europe/Moscow.
