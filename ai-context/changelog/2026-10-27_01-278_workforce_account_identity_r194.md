# 01-278 R194 — собственная смена только по ID аккаунта

Дата: 2026-10-27, Europe/Moscow.
Baseline: 13d1a6e7aec15203a1ea21394b2dad24f736f7ca.

Промт пользователя:

> репо обновлён. проверь остатки по задаче и продолжи

## Контекст и scope

Свежие HEAD/origin/main/actual remote совпали, checkout clean. Проверены R193,
parent R192 и точные 7 paths предыдущего commit. R190 исключил исторический
S2B/backfill тестовых данных; это не разрешение очищать стенд. R194 продолжает
подтверждённый в R193 identity gap workforce, не повторяет завершённые auth steps.

- LoginRedirectController/WorkforceCheckInController передают Authentication.
  WorkforceService принимает bound positive numeric ID + exact raw username,
  читает один account/workforce row по ID и проверяет account enabled/not blocked.
  Удалены trim/lower lookup и String API, ведущие к похожему аккаунту.
- Conditional INSERT проверяет ID/name/account/workforce status до создания
  shift record. Отказ не создаёт смену и не вызывает transport. ON CONFLICT
  сохраняет дедупликацию; identity не подменяется при повторе.
- Ошибка account/workforce read возвращает 503 вместо false из requiresCheckIn.
  Не расширены grants, admin API, recipient/business policy или notifications.
- Raw legacy names, opt-in, расписание, late/unscheduled и overrides сохранены.
  Principal serialization/schema/migration/flags не менялись.
- Новые real login/security/session/CSRF MVC fixtures и 9 PostgreSQL cases;
  прежние fixtures адаптированы к typed actor/status columns без удаления assertions.
  UI source test обновил точное ожидание уже изменённой в R187 версии JS;
  settings template, JS, CSS и зависимости не менялись.

## Валидация

Meaningful RED v6: 36 frozen tests / 23 assertion failures / 13 controls /
0 errors/skips на baseline production bytes. Те же test bytes GREEN.
Frozen source SHA256:
1509ef5fc7d1141a3260c0c0119761a836db91250f9a4b30b1adb84153b58bdb.
MVC проверяет обе очередности case collision после login, exact raw/padded/Unicode
names, long IDs, невалидные direct principals, revoke/read failure, CSRF/anonymous,
routing, opt-in, изменение аккаунта после refresh и перед INSERT, без повторного send.

WorkforceServiceTest: 20 cases на отдельном PostgreSQL Testcontainers;
3 новых checks похожих usernames и 6 изменений account/workforce до INSERT,
плюс все прежние 11 cases. Это реально выполненный SQL, не рабочая БД стенда
и не stress test всех параллельных транзакций.

Итог Maven v2: 16 suites / 598 tests GREEN, failures/errors/skips=0.
Channel rights, login, ambiguity, session refresh/revoke, own password,
admin/self checks и activity regressions включены. Только 3 изменённых production
classes и соответствующие tests компилировались поверх проверенного baseline;
frozen MVC source не менялся после meaningful RED. Native source bytes,
reports, main/sandbox exact scope и normalized SHA проверяются перед closeout.

Настроечные проходы не засчитаны как RED: v1 прерван на медленном Windows mount;
v2 не хватало offline JUnit provider (зависимость загружена в test container);
v3–v5 выявили раннюю datasource wiring, mtime cache и CSRF/Hamcrest record
особенности нового harness. Исправлялся только новый test, не production.
После v6 frozen bytes не менялись. Первый GREEN-кандидат дал 598 tests /
1 failure в прежнем UI source contract: тот ожидал asset version до R187.
Expectation заменён точной текущей версией 20261027-credentials-r187;
остальные assertions и порядок загрузки сохранены. Финальный полный проход v2 GREEN.

Evidence в ignored .tmp: r194-baseline-red-v6.log/reports,
r194-green-v1.log/reports, r194-green-v2.log/reports и r194-result.json.
Source sandbox: .tmp/01-278-r194-workforce-identity.
Native Maven workspace использует отдельный контейнер; тестовый PostgreSQL/Ryuk
не входят в Compose project стенда. Browser/provider/fleet/JDBC session-store
acceptance этим шагом не заявляется.

## Среда и ограничения

Текущая машина: C:\Users\SinicinVV\git_h\tg_ref_b24_sup, Docker тестового стенда.
Свежий read-only probe подтвердил R184 web revision
8310d1f7f0001314abdc60e52516e563168bfee1, container 9fb4a33fd8a4,
image sha256:f2513264a171a8973125a243dbaaf3885fa9161194d5f962d6df7801991253bd,
healthy, restart count 0. Исторический R184 checkpoint/start-stop smoke сохранён.
R185–R194 не развёртывались. Никаких Compose build/up/restart, migrations,
backfill/reset, flags/config/provider sends или изменения рабочих grants/roles.
Данные/схема БД стенда и rollback image/override не затронуты.
Staging/commit/push не выполнялись, команды ручной фиксации не выдаются.

Scope: 3 production Java files, 4 existing tests, 1 новый MVC test,
task details, S0 contract, rule 08 и этот append-only changelog — 12 tracked paths.
Общий статус 01-278 — 🟡. Следующий concrete gap — personal/triage preferences
по ID; остальные scoped data flows/policies/bots и первый production S6 остаются.
