# 01-278 R214 — HTTP-фикстура AI-материалов по ID сессии

Время: 2026-10-28T16:26:42.266Z (UTC), Europe/Moscow.
Baseline 6a5e5d625d9dad1ddf46f2ee1b8b3ca4d573b881, parent 3a62a044e903b9f4a0c18279efcd49c52f5bb62e.
HEAD/origin/main/actual remote совпали; R213 subject/9 paths/content проверены.

Промт пользователя:

> продолжи по задаче

## Изменение

ScopedAiSourceHttpPostgresTest использовал synthetic User без ID и authorities
только в token, из-за чего R189 останавливал все30 сценариев до controller.
Fixture теперь захватывает exact DB ID/raw username до request execution и
создаёт PanelUserPrincipal. Page rights выдаются только при подготовке account;
auth helper выполняет только SELECT, не восстанавливает permissions/account.
Spaced/role-only/dialogs-only cases имеют свои корректные DB accounts/rights.

Сохранены все30 старых case names. Missing/disabled/blocked требуют302/login до
controller согласно R189; foreign/global/collision/archive/revoke business403 и
late precheck/SQL409 сохранены. Прочие role/privacy/CSRF/CAS/opaque key/escaping/
origin/content assertions прежние. Это согласование fixture с действующим main,
не изменение product policy или ослабление ожидаемых business checks.

13 новых cases проверяют6 account revoke/rename/delete/reuse,4 mismatched/unbound
principals,2 revoked DB page rights (включая ROLE_ADMIN без PAGE_KNOWLEDGE_BASE),
1 реальный login с JDBC SESSION и masked CSRF: create DRAFT→review APPROVED/rev2→
reload→page revoke403→logout. Пять prepared entrypoints отклоняются до
availableBusinesses при account/page denial; sources/events не меняются. Новый
granted account с reused name может войти заново; прежний bound ID доступа не даёт.
Page right после запроса остаётся отозванным, repair helper отсутствует.

Общая inherited-container база заменена отдельной fresh r214_source_http_* schema
через существующий PostgresqlJdbcTestSupport. Сохранены те же BotProcessService/
scheduler mocks и test-only storage/config/security properties. Schema prefix
проверяется до fixture writes; readiness/security filters не отключались.
Tracked JDBC helper и PostgresqlIntegrationTestSupport неизменны; штатный helper
остаётся Testcontainers. Product/main/UI/schema/dependencies/policies неизменны.
Scope4 files:1 existing HTTP fixture,task,S0,new changelog; новых Java files нет.

## Проверки

Baseline30 tests/30 assertion failures/0 errors после только datasource isolation:
original test methods/auth/assertions сохранены тогда. После fixture fixes и13
новых cases итог75 tests /2 suites GREEN, failures/errors/skips0:
Source HTTP43 (30+13), R189 SessionAuthority32. Все30 case names вошли в GREEN.
Это test-fixture RED/GREEN, не новая product implementation. Inverse transforms
восстанавливают baseline test file точно; deliberate R189 status updates отражены
выше. 1346 Java cache inputs R213 проверены; compile1 test source в обоих runs,
main не компилировался. Source/class/report SHA/freshness и scope проверены.
JS не запускались повторно: product/UI/JS sources неизменны. R213230 SQL+15 JS —
историческое evidence, не новый run.

Owned native PostgreSQL16.15, 127.0.0.1:54167, fresh isolated schemas, explicit
ignored R200 JDBC supplier с SHA/version/exact data-directory guard. Tracked
Testcontainers helper неизменен; Docker Desktop/Testcontainers не запускались.
JIT off только own test process;83 migration sources неизменны. Flyway применялся
только в test schemas, working migrations не выполнялись. Test server остановлен,
baseline/green reports/log/operators сохранены. Это selected validation, не весь
repository или working/provider acceptance.

Штатный повтор в изолированном test runner с Docker/Testcontainers:

    cd spring-panel
    .\mvnw.cmd "-Dtest=ScopedAiSourceHttpPostgresTest,SessionAuthorityWebMvcTest" test

## Среда и продолжение

Source/test: C:\Users\sushi\Git_H\tg_ref_b24_sup. Рабочий экземпляр на другой
машине/сессии: C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Полные R184/R198/R204/R207
runtime/rollback/V83 backup blocks сохранены. Последний документированный runtime
R207: webR206/workerR198/bot-runner прежний/foundation off. Deploy/restart/working
migrations/flags/provider calls из source-сессии не выполнялись. R214 test-only
не требует пересборки/перезапуска. Предыдущие product source R208–R213 этим шагом
не развёрнуты; новых migrations нет, V83 не повторять.

Source createKnowledge/createMemory/revise/read/configure/retrieval/handoff SQL
identity остаётся String boundary; ID-binding в этот шаг не включён. Также открыты
child/media/feedback/GENERAL/policy/privacy/delivery/fleet/flags/S6. Task🟡;
historical S2B снят R190. Source apply отделён от Git publisher/deploy; commit/push
разрешены до конца2026-10-28 Europe/Moscow с expiry guard. Старые changelogs сохранены.
