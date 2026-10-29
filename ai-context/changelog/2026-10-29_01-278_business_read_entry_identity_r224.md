# 01-278 R224 — ID сессии на входе canonical BUSINESS API

Время: 2026-10-29, 11:19 +03:00; текущая Windows/Docker-сессия рабочего экземпляра.

Промт пользователя:

> репо обновлён. проверь и при необходимости запусти пересборку. далее продолжи по задаче

## Изменение

После отдельной выкатки R223 продолжен source scope. Baseline HEAD/origin/main/
actual remote aa69238ca88000e99b7503d4132485627713397f, parent
1d7f1feaa88da2f328e48454601d6b51796926e7; checkout чистый, R222 сохранён.

BusinessScopedTicketReadService.requireAuthenticatedUserId больше не принимает
String и не выбирает аккаунт по lower(username). Требует Authentication с bound
positive PanelUserPrincipal, exact raw username/valid principal status, затем
SQL по собственным ID/name/enabled/not blocked и прежней case-fold uniqueness.
Rename/delete+reused name, case change и status revoke до entry SQL дают отказ.
Ошибки SQL не превращаются в fallback. Trim/Java folding/новая login policy нет.

Все12 entry calls BusinessScopeReadController и manual creation controller
передают исходную Authentication. Прежние page/flags/business READ/OPERATE,
owner/media proof, downstream numeric-ID readers и manual writer неизменны.
Это entry-time guard, не global/response-wide revocation lock. Account changes
после entry и атомарные child/manual write boundaries ещё требуют отдельной работы.

## Проверки

99 tests/6 suites одним selected run GREEN, failures/errors/skips0:
новые SQL41/HTTP23 и прежние SQL12/controller13/manualMVC2/media8.
Frozen baseline v2:64 cases/25 assertion failures/errors0, controls39 GREEN;
после исправления те же source bytes/3 compiled class hashes/case names GREEN.
Проверены raw names, Unicode, BIGINT, invalid/legacy principals, own status/NULL,
reuse/rename/delete/case, conservative ambiguity, storage errors и read-only
account snapshots. HTTP использует настоящий login/R189/session/CSRF с поздними
мутациями до resolver; downstream business list/manual writer изолированы.
Сохранены page/flag/anonymous gates, CSRF, private headers и исходный actor ID.

Первый red run64 имел15 SQL +2 HTTP assertion failures и20 HTTP setup errors:
тестовый csrf() менял token repository для следующих login requests. Только новая
фикстура исправлена на реальный cookie/form token; v1 artifacts сохранены.
Повторный v2 RED выполнен на неизменном product до правки; его assertions далее
не менялись. Существующие assertions не ослаблялись: 4 fixtures адаптируют13
name-match mocks,1 exact Authentication input,2 bound login inputs и2 production
status columns в тестовой users table; deterministic inverse восстанавливает baseline.

1360 Java/83 migration inputs совпали с candidate,1839 inputs всего,4 generated
CSS EOL-only после Sass. Полный main/test compile JDK17, ограниченный helper896MiB;
Testcontainers PostgreSQL/fresh schemas без рабочего datasource. XML свежие,
source/class/XML hashes, exact scope/replay/diff guards проверены до main apply.
JS/UI/SCSS/dependencies/serialized principal/schema/grants/flags прежние.

## Scope и остаток

3 main files,4 adapted old tests,2 new tests,task/S0/этот changelog. Отдельный
R223 changelog фиксирует rollout, который уже выполнен и не входит в этот
source validation. Source apply без staging/commit/push/deploy/migration/backfill.
Стенд остаётся R222/workerR198/foundationOFF; R224 ещё не в web image.

Task document обновлён по реально выполненному и оставшемуся. Ближайшие пути:
legacy AttachmentService HTTP business READ до binary open (PAGE_DIALOGS и
уникальность root/metadata недостаточны), GENERAL String entry lookup, final
account/child/media/edit/feedback/realtime proofs, task/project/system policies,
AI privacy/retention/delivery, совместимый fleet/controlled flags/S6. Прежний
health503 из-за DLQ1 не скрыт; queue не трогалась. Historical S2B снятR190,
задача🟡. Evidence .tmp/01-278-r224-evidence/; runtime/rollback — R223.
