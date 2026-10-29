# 01-278 R227 — восстановлены GENERAL-регрессии после identity guards и миграций

Время: 2026-10-29T13:07:13.099Z (UTC; Europe/Moscow +03:00).
Baseline 3f16baa81047fb923c9c8eebb0d26badc930ddaf, parent 5eeb95e1bc35a57220bbb9759ab885d25e1fa8ef.
HEAD/origin/main/actual remote совпали; 3 новых commits R224/R225/R226,
их parents/subjects и43 unique paths/current content проверены и сохранены.
Изучен rolloutR223 на другой машине, здесь не повторялся.

Промт пользователя:

> репо обновлён. проверь и продолжай по задаче

## Изменения

GeneralTicketWorkHttpPostgresTest.actor создаёт bound PanelUserPrincipal с ID/
exact name и сохраняет требуемые authorities в test DB. Полная security chain,
R189/session refresh, CSRF и все прежние HTTP expectations остаются. Шесть новых
cases подтверждают отказ unbound, page revoke и disabled/blocked/renamed/deleted.
Это injected bound session fixture; реальные form-login suites повторены отдельно.

GeneralManualTicketCreationPostgresTest, GeneralTicketDocumentReplyPostgresTest
и GeneralTicketReplyChangePostgresTest проверяют upgrades V69/V71/V70→V83:
равенство всех старых columns/values/history/grants, exact old+6 column set,
nullable business_selection_source/questionnaire_catalog_sha256/questionnaire_version,
responsibility_revision0/business_work_revision0/business_categories[]. Не скрыты
старые изменившиеся values или будущие новые columns. Historical S2B не возвращён.
Scope7:4 existing tests, task detail01-278, S0 contract, этот append-only changelog.
Main/schema/UI/JS/dependencies/flags не менялись.

## Проверки

Свежий неизменный baseline четырёх полных suites: 343 cases,89 failures,
errors0, controls254; HTTP86+3 snapshot failures. R226 baseline135
был selected subset, за нынешний full4 не выдаётся. После исправления
586 tests /11 suites GREEN одним selected run, failures/errors/skips0.
Прежние 343+6 новых и237 unchanged GENERAL/BUSINESS entry/read/media cases,
в том числе148 frozen identity SQL/real form-login cases R226. Все baseline case
names сохранены, main/unmodified test class bytes прежние. Старые HTTP assertions
не менялись, migration comparisons сохраняют все старые поля и проверяют новые.
Исторический779/14 R226 остаётся non-GREEN; полного rerun779 здесь не было.

Full compile Maven wrapper/JDK17 main731/test637,1368 Java/83 migrations,
1801 inputs; после edits compiled4 tests. Native PostgreSQL16.15,
loopback54167/fresh schemas, explicit ignored R200 supplier с SHA/version/exact
directory guards; tracked helper/base прежние. V1 дал176 setup errors, до сценариев:
Maven перекомпилировал helper после старого copied adapter mtime, Testcontainers
не нашёл Docker. Все4 XML/log сохранены; adapter mtime/class восстановлены,
после этого v2 baseline иv3 GREEN. Docker/containers не запускались, owned server
остановлен, artifacts сохранены. SQL migrations только test schemas, JS не rerun.
Fresh XML/log/source/class SHA, scope/replay/inverse/diff checks до root apply.
Selected validation, не whole repository/runtime/provider acceptance.

## Среда и остаток

Source/test C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочий экземпляр на другой
машине/сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Полные R184/R198/R204/
R207/R216/R223 runtime/rollback/V83 blocks сохранены. Последний runtimeR223:
webR222/workerR198/foundationOFF/readiness200UP/health503DOWN из-за прежнего DLQ1.
Рабочие DB/deploy/restart/migrations/flags/queue/provider не затрагивались,
V83 не повторять. R224–R226 ещё требуют web rollout; R227 меняет только tests/docs.

Долг этих4 suites закрыт. Следующий функциональный пакет — GENERAL work/write/
SSE ID-bound Authentication; post-entry/admin grant atomicity, child/media/
feedback/GENERAL/realtime/policy/AI/fleet/flags/S6 остаются. S2B снятR190, Task🟡.
Source apply/Git publisher/runtime разделены, ранее разрешённые commit/push
действуют до конца29 октября Europe/Moscow. Git-факты: .tmp/r227-result.json,
r227-final.json; proof: r227-baseline-red/java-green/validation.json и logs/XML.
