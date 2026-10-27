# 01-278 R196 — личные Bitrix24/iiko credentials по ID

Дата: 2026-10-27 (Europe/Moscow).
Baseline: 181e04ba6b65cbae6d66721d5cbe91e2616ba38e.

Промт пользователя:

> репо обновлён. проверь изменения и продолжи в рамках задачи

## Изменение

Проверены свежие clean HEAD/origin/main/actual remote и R194/R195:
parent, subjects и точные 12/22 paths; R193 attested по прежнему artifact.
Уже внесённые ID-bound check-in и UI/triage preferences сохранены.

R196 переводит личные integration secrets на Authentication/PanelUserPrincipal
и canonical numeric account ID. Новый namespace:
employee_discount_automation_credentials.account.v2. Нет name trim/case-fold,
v1 fallback/auto-import. Account guard и FOR SHARE/FOR UPDATE работают внутри
REQUIRES_NEW read/merge/write; update CAS проверяет JSON/type/key/status/count.
409 для duplicate/corrupt/changed storage, 503 для SQL/transaction failures.
Partial merge, secret retention, URL profile selection/removal, clear selections,
UTC и masked client view сохранены. Raw username остаётся display value.

Полный Authentication проходит controller → automation service → Bitrix24/iiko
для status/save, справочников, preview/discovery/run mutations. Весь private
namespace, включая v1, закрыт от generic settings CRUD/list/model даже admin;
ui_preferences.* R195 остаётся закрытым. Rotation registry использует новый
numeric namespace и account ID + SHA-256 URL-key вместо collision-prone slug.
Noncanonical/duplicate keys не выбираются как один корректный источник.

Scope: 8 production Java, 9 адаптированных existing test files, 2 новых test
files и 4 документа (task, S0, rule 08, этот changelog): 23 paths.
Прежние assertions сохранены, меняются identity fixtures/call signatures,
v2 keys и ожидаемый fail-closed HTTP exception contract. Assertion counts проверены.
Исторические changelogs и полный R184 checkpoint оставлены без изменений.

## Валидация

31 suites / 862 tests GREEN; failures/errors/skips=0. Fresh compile 728 main / 584 test
sources, без borrowed cached bytecode. 56 frozen new cases: baseline v6
53 assertion failures / 3 controls / 0 errors/skips; те же source bytes GREEN.
42 security/MVC cases и 14 registry cases. Source/class/report SHA и freshness
проверяются отдельно перед source apply. Существующие R184/R188–R194 security
regressions, R195 controllers/snapshot и affected automation suites включены.

Real login/DAO/session/CSRF, stateful JDBC/transaction adapters: collision after
login, raw/padded/Unicode/anonymousUser names, rename/reused names, Long IDs,
invalid principals, account status guard после refresh, corrupt/duplicate rows,
storage failure и generic settings bypass. Actual Bitrix24/iiko adapters проверены
через HTTP server на loopback; внешние provider APIs не вызывались.
EmployeeDiscountAutomationCredentialServiceTest: два failure methods GREEN;
адаптированный PostgreSQL persistence method только скомпилирован.

V1–V4 нового harness не засчитаны как RED: ошибки imports/generic result type.
V5 дал 54 failures/2 controls, из них один — неверное новое ожидание redirect
при API CSRF. Оно исправлено на HTTP 403; финальный RED v6 выполнен снова на
неизменённом production baseline. Артефакты всех попыток сохранены в .tmp.
Docs v1 guard остановил неверный heading anchor до записи документов;
v2 использует фактический заголовок свежего R195. Production source не менялся.
Это не доказательство PostgreSQL locks/concurrent first-save/rollback,
browser, JDBC session-store, fleet или live provider acceptance.

## Среда и остатки

Сессия: C:\Users\sushi\Git_H\tg_ref_b24_sup, source/test checkout.
Стенд/Docker: C:\Users\SinicinVV\git_h\tg_ref_b24_sup, другая машина/сессия.
Read-only docker info здесь: Docker daemon unavailable. Runtime probe R195
подтвердил R184 в другой сессии; из этой сессии он не повторён. R185–R196
не развёртывались отсюда. Working DB/schema/data, roles/grants, flags/config,
providers, rollback tag/override R184 не менялись. Новых migrations/dependencies нет.
Historical S2B снят R190; старые test rows не удаляются/переносятся этим шагом.

Следом — отдельный PostgreSQL locking/rollback/merge acceptance и ID-bound run
history: actor.trim/ActorIgnoreCase queries пока остались, shared/global automation
policy не меняется. S3–S6, scoped pickers/data, AI/bots, manual/global/system policy
и первый production запуск открыты; общий статус 🟡.
Source apply и Git publisher разделены; разрешение commit/push до конца
2026-10-27 Europe/Moscow соблюдается отдельным expiry guard. Deploy не выполняется.
