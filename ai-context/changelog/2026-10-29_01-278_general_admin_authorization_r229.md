# 01-278 R229 — атомарная авторизация управления GENERAL-доступом

Время: 2026-10-29T16:54:00.044Z (UTC; Europe/Moscow +03:00).
Baseline 586e9d3731ad00b3ad01e780d6312d4eeecdfdea, parent 0ea477d630d2316c3d7a0d00fa788d7c09aa5e10; HEAD/origin/main/actual remote
совпали, R22827 paths/content проверены и сохранены.

Промт пользователя:

> давай дальше

GeneralTicketAccessService.accounts/grantRead/revokeRead/grantOperate/revokeOperate
принимают исходный Authentication, без actorless API. Controller передаёт actor,
early identity gate/@PreAuthorize/CSRF/feature flags/statuses/success logs прежние.
Owning SQL соединяет current bound ID/raw-name account с эффективными системными
ROLE_ADMIN+PAGE_SETTINGS, блокирует используемые account/authority/assigned-role
rows. Direct/role/mixed sources сохраняют login policy; role snapshot вычисляет
флаги существующими UserRepository page parsers и Java trim/ignore-case, затем SQL
сверяет current role assignment и exact raw metadata. Role lookup сам по себе не
разрешает действие; замена после capture не проходит conditional gate.

Late admin deny403 и private/no-store account listing. READ target assignment
по-прежнему допускает inactive target, дальнейшее чтение требует CURRENT_ACCOUNT;
OPERATE требует eligible target+active READ. READ revoke cascades OPERATE,
idempotency сохранена. Business/portal roles и username admin не обходят gate;
системному админу GENERAL grants не нужны. Нет нового grant audit schema.
Scope20:service/controller/new security snapshot,11 existing tests,3 new tests,
task/S0/этот append-only changelog. UI/JS/schema/dependencies/flags unchanged.

1108 tests /14 suites GREEN одним selected run, failures/errors/skips0:
365 новых (246 SQL/119 real-form-login HTTP) и743 прежних cases, включая30 selected
GENERAL/BUSINESS media/followup cases из6 methods. Прежние result assertions
сохранены;11 fixture files теперь передают отдельного bound test administrator
с ROLE_ADMIN+PAGE_SETTINGS, без GENERAL grants и без повышения прав получателя.
Minimal fixtures явно подготавливают roles/role_id; production schema неизменна.
Fresh expanded baseline на unchanged product:365 cases/200 assertion failures/
0 errors/controls165. Frozen cases/source/classes одинаковы до/после; проверены
свежие XML/log/source/class SHA. Это selected validation, не весь repository,
реальный Redis/provider или рабочая acceptance. 529 cases R228 здесь не rerun:
их source/class hashes и CURRENT/READ/OPERATE policy прежние; свежий1287/13 R228
остаётся историческим отдельным run, не суммируется с нынешним.

Первый frozen baseline185 дал115 failures/0 errors/70 controls. Первый SQL-вариант
учитывал только direct authorities; во время review подтверждено наследование
из roles в UserRepositoryUserDetailsService. Selected runv2 остановлен как
неполный, его XML/log сохранены;13 sandbox files восстановлены checked inverse
к baseline. До product v2 добавлены180 role-source/race/storage cases без
ослабления прежних assertions. Затем v3 expanded RED365/200/0; финальный run
v4 GREEN. UserRepositoryUserDetailsService не менялся: новый snapshot
использует его существующие page-permission parsers и ту же Java trim/ignore-case
проверку имени admin. Неверный JSON не даёт наследуемого PAGE_SETTINGS; direct
valid rights сохраняются. Wildcard/mixed sources/portal/Unicode проверены.

30 concurrency cases наблюдают реальный PostgreSQL backend Lock wait перед
committed account/direct/assigned-role revoke;15 cases меняют role source после
capture и до owning SQL. Проверены raw/Unicode/BIGINT, recipient eligibility,
idempotence, storage failure, real login/CSRF/R189/private headers. Authorization
locks сериализуют с revoke, не утверждается победа revoke после захвата locks.
Fresh cached1371Java/2226classes, changed sources compiled Maven/JDK17; native
PostgreSQL16.15/fresh schemas, ignored R200 supplier SHA/version/directory guards,
tracked supplier/83 migration sources прежние. Migrations только test schemas.
Docker не запускался, owned PG остановлен, artifacts retained; JS не rerun.
Exact scope/content/replay/inverse/source/class/XML/log proof перед root apply.

Source/test машина C:\Users\sushi\Git_H\tg_ref_b24_sup; рабочий экземпляр на другой
машине/сессии C:\Users\SinicinVV\git_h\tg_ref_b24_sup. Последний документированный
runtimeR223: webR222/workerR198/foundationOFF/readiness200UP/health503DOWN из-за
прежнего inboundDLQ1. Полные R184/R198/R204/R207/R216/R223 runtime/rollback/V83
blocks сохранены. Working DB/deploy/restart/flags/provider/queue не затрагивались;
V83 не повторять. R224–R226 и R228–R229 требуют будущего panel-web image;
R227 tests/docs отдельного rollout не требует. Развёртывание R229 не заявляется.

Далее — сквозные child/media/edit/feedback/realtime boundaries, оставшиеся
admin/boundary проверки, PENDING product policies для task/project/system/global,
AI privacy/context/retention/delivery, совместимый fleet/flags/S6 acceptance и
операционный DLQ gate. СнятыйR190 S2B не возвращён. Общий статус — 🟡.
Commit/push разрешены до конца29 октября Europe/Moscow; source apply, отдельный
Git publisher и deployment разделены. Git-факты — r229-result/final.json.
