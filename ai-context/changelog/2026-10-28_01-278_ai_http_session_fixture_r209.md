# 01-278 R209 — AI HTTP-фикстура с ID сессии

Дата: 2026-10-28T11:53:14.255Z (UTC), Europe/Moscow. Baseline c20f9f7553d211f8e0051f223a60120cbdbd984a,
parent a8ac417ce79aebac0e6f67d2bfc13ad428511558. Subject/11 paths/content R208 проверены.

Промт пользователя:

> делай. желательно одним проходом

## Изменение

Закрыт обозначенный R208 долг ScopedDialogAiHttpPostgresTest: прежний user(String)
стал ID-bound PanelUserPrincipal с exact raw именем. Аккаунт seed-ит DB PAGE_DIALOGS/
PAGE_KNOWLEDGE_BASE, auth helper не пишет grants и не нормализует имя. R189 остаётся
реальным и перечитывает ID/current status/DB rights. Padded negatives получают
literal отдельные аккаунты; private404 и положительный raw audit сохранены.
ROLE_ADMIN без PAGE_DIALOGS — отдельный DB account; KB revoke также в БД.

Единственное изменённое прежнее response expectation: blocked404 → exact302/login,
как требует существующий R189 до controller. Дополнительно never(resolve) и zero
control/events/memory writes. Иные прежние privacy/CSRF/CAS/curation/archive/late
revoke/evidence assertions сохранены. Inverse transforms восстанавливают baseline
fixture целиком; это не claim, что blocked assertion не менялся.

13 новых cases:6 account disable/block/delete/rename/rename-reuse/delete-reuse после
подготовки запросов;4 unbound/foreign-ID/case/padding;2 DB PAGE_DIALOGS/KB revoke без
repair;1 real JDBC login. Replacement account может получить новую отдельную сессию,
подготовленная старая не возрождается. GET/control/memory не достигают controller
после revoke/mismatch; отказы не создают writes. Отзыв KB оставляет control OPERATE.

Real-login case использует настоящий form login, SESSION cookie JDBC store и actual
cookie/token CSRF. Проверены private view, MEMORY DRAFT с полным origin, control/raw
actor ID/name, CAS replay409, DB revoke→403/no extra writes и logout→login. Cookie jar
сохраняет неизменённые cookies и удаляет истёкшие. Нет injected principal/MockHttpSession/
CSRF postprocessor в этом case; старый CSRF helper остальных cases сохранён.

## Проверки

Baseline полный прежний suite на неизменном HEAD:22 tests/22 failures/0 errors,
skips0. Отчёт .tmp/r209-baseline-ScopedDialogAiHttpPostgresTest.xml сохранён.
Final 84 tests /3 selected suites GREEN, failures/errors/skips0:35 HTTP
(22 прежних case names точно сверены +13 новых),17 AiControlActorIdentityWebMvcTest,
32 SessionAuthorityWebMvcTest. Source/class/report hashes/freshness проверены.
Один изменённый test source собран свежим javac17;1336 unchanged Java inputs cache
R208 проверены. Production/main/UI/dependencies и83 migration sources неизменны.
15 BUSINESS JS и прочие SQL suites R208 не перезапускались для test-only scope;
их прежние GREEN reports не объявляются новым evidence.

Тесты на owned native PostgreSQL16.15, 127.0.0.1:54167, fresh schemas в .tmp/r200-pg/data,
explicit ignored R200 JDBC supplier adapter с exact data-directory/version guard.
Tracked supplier не менялся, Testcontainers не выполнялся; Docker Desktop не запускался.
JIT off только в own test process; Flyway83 только в test schema. Native server остановлен,
artifacts сохранены. Это selected suite, не весь repository или working/provider acceptance.

## Среда и продолжение

Source/test C:\Users\sushi\Git_H\tg_ref_b24_sup. Рабочий проект на другой машине/
сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Полные R184/R198/R204/R207 runtime,
rollback и V83 backups сохранены. Runtime остаётся R207:panel-webR206/workerR198,
foundation off; bot-runner прежний. Remote/build/restart/deploy/working migrations/
flags/provider calls здесь не выполнялись. R209 test-only не требует rebuild/restart.
Product R208 ещё требует отдельного panel-web rollout; новых migrations нет.

Штатный повтор с Docker/Testcontainers в изолированном test runner:

    cd spring-panel
    .\mvnw.cmd "-Dtest=ScopedDialogAiHttpPostgresTest,AiControlActorIdentityWebMvcTest,SessionAuthorityWebMvcTest" test

Scope4 paths: этот changelog, task01-278, S0 и один существующий Java test. Старые
changelogs не переписывались. Общая задача остаётся🟡; AI non-CAS/reads/memory/source/
handoff identity, child/media/feedback/GENERAL/policy/privacy/delivery/fleet/flags/S6
остаются отдельными шагами. Historical S2B снят R190. Source apply без staging/Git/deploy;
отдельный publisher использует разрешение commit/push до конца2026-10-28 Europe/Moscow.
