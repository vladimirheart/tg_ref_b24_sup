# 01-278 R215 — создание AI-материалов по ID сессии

Дата: 2026-10-28, Europe/Moscow. Baseline 752309d0dedeed4f404c190a300c230d12575830,
parent 6a5e5d625d9dad1ddf46f2ee1b8b3ca4d573b881. HEAD/origin/main/actual remote совпали;
R214 subject/4 paths/content проверены.

Промт пользователя:

> продолжай

## Изменение

После HTTP CONFIGURE precheck логин автора мог перейти к другому аккаунту.
createKnowledge/createMemory в ScopedAiSourceService теперь получают bound
Authentication; principal guard требует positive ID/raw name/valid statuses.
Сам INSERT проверяет actor.id=session ID и actor.username=exact raw name.
Source и audit event записывают ID и stored raw username, без lowercasing audit.
String-only create overloads удалены, controller передаёт Authentication.

Unique enabled/non-blocked account, active business, READ/ADMIN CONFIGURE и полный
MEMORY origin tuple/collision denial сохраняются в том же SQL statement.
DRAFT/revision1, title/body trimming/limits, opaque key, повтор ключа и atomic
source/audit rollback сохранены; late HTTP denial409/no-store без partial write.
Гарантия statement-time, не global revocation после начала SQL snapshot.

Source revise/read/configure/retrieval/handoff и workbench/persistence не менялись.
UI/schema/dependencies/policies не менялись;83 migration sources прежние.
Scope13 files:2 main,6 existing test/support,2 new tests,task/S0/changelog.
5 прежних fixtures адаптированы к Authentication через initial-login fixture:
unique case-insensitive lookup, stored ID/raw name, без repair accounts/grants.
Прежние assertions сохранены; inverse transforms восстанавливают baseline точно.

## Проверки

344 tests /7 suites GREEN, failures/errors/skips0. 118 новых cases:
100 SQL+18 actual form-login/CSRF HTTP; baseline118/40 assertion failures/0 errors
на неизменном product code, controls78. Те же frozen source bytes и118 case
names GREEN. Полностью повторены source SQL82, workbench47, principal19,
source HTTP43 и dialog HTTP35.
Проверены mismatched/missing IDs/raw names, reused login, invalid auth, BIGINT,
literal source/event audit, live account/uniqueness/business/roles/grants, late
statement-time mutations, atomic audit failure/idempotency. HTTP меняет автора
после реального login/R189 refresh/CONFIGURE перед create; проверены409/no-store,
stored raw name, ADMIN/ALL_ADMIN, page authority/CSRF/anonymous/flags и repeated form.

Первый setup v1: HTTP18/11 assertion failures/0 errors, включая лишние403 из-за
отсутствия display_name в test businesses. SQL100/36/0 уже воспроизводил issue.
V2 добавляет только test column; новая SQL class использует fresh schema один раз
на класс и полный reset fixture rows перед каждым case. Все SQL/HTTP assertions
сохранены; manifest v2 зафиксирован до product edit. V1 setup/log/source manifest
сохранены. V2 baseline reports/log SHA, final source/class/XML/log SHA/freshness и
case names проверены. 1346 cache inputs R214 подтверждены; emitted Java main/test
code скомпилирован Maven wrapper/JDK17. JS не rerun, старые evidence — история.

Native PostgreSQL16.15/loopback54167; explicit ignored R200 native JDBC supplier
с SHA/version/exact data-directory guards и fresh test schemas. Tracked JDBC
helper/base неизменны; штатный helper использует Testcontainers.
Docker/Testcontainers не запускались. Flyway только isolated test schemas; все83
migrations неизменны. Owned test PostgreSQL остановлен; artifacts сохранены.
Это selected validation, не full repository/runtime/provider acceptance.

## Среда и остаток

Source/test C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочий экземпляр на другой машине/
сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Последний документированный runtime
R207: webR206/workerR198, bot-runner прежний, foundation off. Полные R184/R198/
R204/R207 runtime/rollback/V83 blocks сохранены. Deploy/restart/working migrations/
flags/provider calls не выполнялись. Для применения нужен новый panel-web image;
R208–R215 этим source-шагом не развёрнуты. Новых migrations нет, V83 не повторять.

Остаются source revise/read/configure/retrieval/handoff identity, другие child/
media/feedback/GENERAL, task/project/system/admin policy, AI privacy/delivery/
auto-reply, controlled flags/fleet/provider acceptance и первый production S6.
Historical S2B снят R190, общий task status🟡. Commit/push разрешены до конца
2026-10-28 Europe/Moscow; Git publisher отделён от source apply/deploy.
Evidence: .tmp/r215-baseline-red.json, r215-java-green.json, r215-validation.json,
r215-result.json, r215-final.json. Эти local ignored evidence не входят в commit.
