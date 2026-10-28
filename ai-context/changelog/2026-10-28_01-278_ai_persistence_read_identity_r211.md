# 01-278 R211 — AI-состояние и настройки по ID сессии

Дата: 2026-10-28T13:30:56.203Z (UTC), Europe/Moscow. Baseline c448ce463b5fb31c89e38e72310a4decaa9065c9, parent 36646e41b51d47cc7170e9581fadffc72d8c1fbc.
Проверены R210 subject/12 paths/content, HEAD/origin/main/actual remote совпали.

Промт пользователя:

> давай дальше

## Изменение

findControl/canOperate/findState/findEvents ScopedDialogAiPersistenceService принимают
Authentication controller. Общий R208 guard проверяет authenticated/non-anonymous
bound PanelUserPrincipal, positive ID/raw name/status. Read JOIN требует actor ID и
exact username; нормализованное имя остаётся только для прежнего READ/uniqueness.
Текущие account/business/grants и отдельный OPERATE проверяются тем же statement.
String-only overload этих четырёх API удалён, public route/response не менялись.

Полный owner tuple, control/state matching, collision/GENERAL/unresolved denial,
legacy fallback refusal и event descending/cap1..50 сохранены. Authorized absence
control даёт прежние false/null defaults; unauthorized reader получает empty.
VIEWER читает, OPERATOR/MANAGER/ADMIN управляют по текущим независимым grants.

HTTP late denial: findControl→private404/no-store; canOperate→false, state→null,
events→пустой список. Уже законно прочитанные поля остаются в model. Гарантия только
на момент SQL statement, не atomic whole-page revocation после SQL snapshot.
PAGE_DIALOGS/KB/CSRF/flags сохранены. Тела recordHandoff/setControl/setControlExpected
точно совпали с baseline: normalization, raw audit, CAS и rollback не менялись.
Workbench service/MEMORY/source/handoff, UI/schema/dependencies/policy/grants вне scope.

## Проверки

56 SQL scenarios исполняют все четыре API на общем snapshot,31 real-login HTTP —
изменения после R189 refresh/owner lookup перед выбранным чтением. Frozen baseline: SQL v3 56/18/0
+ HTTP v2 31/12/0 =87 tests/30 failures/0 errors,57 controls. Те же final source bytes GREEN. Final
314 tests /9 selected suites +15 BUSINESS JS GREEN, failures/errors/skips0.
Persistence58/workbench47/ScopedPrincipal19/R20935/R20817/R210 HTTP19/R18932 включены.
Четыре прежних fixtures/48 calls адаптированы только к bound identity; assertions
не менялись, inverse transforms восстанавливают baseline точно. Known fixture IDs
моделируют initial case-insensitive login с stored raw name без lookup при чтении.
New tests проверяют stale/mismatched/missing IDs/raw names, invalid auth, BIGINT,
grants/status/uniqueness/business, owner/record matching, defaults и event cap/order.
Подготовленные старые сессии не выбирают replacement account по reused login.

Setup v1 остановлен на compile: отсутствовал import SoftAssertions, tests не шли.
V2 добавил только import; assertions сохранены. Затем три SQL owner fixtures
дали errors при UPDATE immutable AI rows:56 tests/18 failures/3 errors. V3 меняет
текущий tickets owner и expected fresh scope вместо сохранённых AI rows; защиты БД
не отключались, ожидания empty/OPERATE сохранены. SQL v3 повторён целиком, HTTP v2/
support не менялись. V1/v2 source/manifest/XML/log остались; final frozen v3 SHA/
case names сопоставлены с GREEN XML. Source/class/report SHA/freshness
и три writer bodies проверены. 1339 cache Java inputs R210 проверены; baseline
compile3 новых sources, v3 compile1 SQL fixture, final compile2 main+4 adapted fixtures. Reflection bridge
только в новых tests выбирает old/new API, identity parameter index1 явно проверен.

Тесты на owned native PostgreSQL16.15, 127.0.0.1:54167, fresh schemas .tmp/r200-pg/data,
explicit ignored R200 JDBC supplier с exact data-directory/version guard. Tracked
helper без изменений; Testcontainers не выполнялся, Docker Desktop не запускался.
JIT off только в own test process; relations ANALYZE. 83 migration sources неизменны,
V62/Flyway только test schemas. Native server остановлен, artifacts сохранены.
Это selected suite, не весь repository или working/provider acceptance.

Штатный повтор с Docker/Testcontainers в изолированном test runner:

    cd spring-panel
    .\mvnw.cmd "-Dtest=AiPersistenceReadIdentityPostgresTest,AiPersistenceReadIdentityWebMvcTest,ScopedAiPersistencePostgresTest,ScopedDialogAiWorkbenchPostgresTest,ScopedPrincipalIdentityPostgresTest,ScopedDialogAiHttpPostgresTest,AiControlActorIdentityWebMvcTest,AiWorkbenchReadIdentityWebMvcTest,SessionAuthorityWebMvcTest" test

## Среда и продолжение

Source/test C:\Users\sushi\Git_H\tg_ref_b24_sup. Рабочий проект на другой машине/
сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Runtime R207: webR206/workerR198,
bot-runner прежний, foundation off. Полные R184/R198/R204/R207 runtime/rollback/V83
backup blocks сохранены. Remote/build/restart/deploy/working migrations/flags/provider
calls здесь не выполнялись. Для применения R208/R210/R211 нужен новый panel-web image;
новых migrations нет, V83 не повторять. Исторические changelogs не переписывались.

Scope12:2 main,4 existing fixtures,3 new tests/support,task/S0/этот changelog. Шесть
workbench/persistence reads bound по R210/R211, но source/handoff reads не закрыты.
Non-CAS setControl(String), MEMORY createMemory(String), source/configure/retrieval/
handoff identity, child/media/feedback/GENERAL/policy/privacy/delivery/fleet/flags/S6
остаются. Task🟡, historical S2B снят R190. Source apply отделён от Git publisher/
deploy; commit/push разрешены до конца2026-10-28 Europe/Moscow, с expiry guard.
