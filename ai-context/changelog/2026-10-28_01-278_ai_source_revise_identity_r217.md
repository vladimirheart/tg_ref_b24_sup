# 01-278 R217 — утверждение AI-материалов по ID сессии

Дата: 2026-10-28, Europe/Moscow. Baseline HEAD/origin/main/actual remote
`18a00b3dc38ba7b2624cca40deda9be941a28c77` (R215), clean до source apply.
На этой машине отдельно выполнен web-only rollout R216, описанный в своём changelog.

Промт пользователя:

> репо обновлён. проверь и при необходимости запусти пересборку. далее продолжи по задаче

## Исправление

После HTTP CONFIGURE precheck логин утверждающего мог перейти к другому аккаунту.
`revise`/`reviseExpected` ScopedAiSourceService теперь получают bound Authentication;
общий с create guard проверяет positive ID/raw username/statuses. UPDATE требует
`actor.id=:actorId AND actor.username=:actorName`, source и revise-event сохраняют
numeric ID и точный raw username. Контроллер передаёт Authentication, String-only
review overloads удалены. Private helper обобщён для create/revise.

Unique enabled/non-blocked account, active business, READ/ADMIN CONFIGURE и MEMORY
origin tuple/collision denial сохранены в том же SQL. CAS revision/business/kind/
key и immutable source ID для HTTP, content trim/limits, explicit review/status,
повторы и atomic source/audit rollback прежние; late HTTP409/no-store.
Гарантия statement-time, без global lock/post-snapshot revocation обещаний.
Read/configure/retrieval/handoff APIs, UI/schema/policy/dependencies/flags не менялись.

## Доказательства

570 tests /9 suites GREEN (0 failures/errors/skips).226 новых frozen cases:
200SQL+26 real-login/CSRF HTTP. Baseline R215:226/78 assertion failures/0 errors,
controls148. Те же source bytes/3 class hashes/case names GREEN после исправления.
Повторены344 прежних теста/7 suites, в том числе create R215 и5 прежних fixtures.
Только34 review identity arguments в4 fixtures обёрнуты existing initial-login
helper; inverse восстанавливает baseline, assertions/data providers не ослаблялись.

Проверены ID/name mismatch, rename/delete/reuse/case/padding, invalid auth,
BIGINT/raw audit, current roles/status/ALL_BUSINESSES/uniqueness, late SQL/HTTP
изменения, MEMORY provenance, foreign business/kind/ID/revision, CSRF/page/flag,
idempotency и audit failure rollback. UI/JS не менялись, JS suites не rerun.

Maven/JDK17, isolated PostgreSQL16 Testcontainers/fresh schemas.1350 Java и83
migration raw input hashes совпали с compiler inputs; всего1829 inputs, кроме4
generated CSS с доказанным EOL-only отличием штатного Sass plugin. Working datasource
не использовался, migrations только в test schemas, provider calls отсутствуют.
Подготовительный compile-v1 исправлен explicit seed imports до freeze-v2, без
изменения assertions. Post-run verifier v1/v2 ошибался на PS array/extended string
serialization; v3 сравнивает данные. Эти RED artifacts сохранены; Java/tests и
фактический570/9 GREEN не менялись. Detached replay, same patch/content и exact
path/hash guards перед main apply; evidence `.tmp/01-278-r217-evidence/`.

## Граница результата

8 source/test paths +4 documentation paths (task/S0/два changelog R216/R217).
Рабочий web остаётся clean R215/rollout R216, workerR198, foundationOFF: **R217
не развёрнут** и требует отдельной сборки web. Сохраняются R216 rollback/health
диагностика и прежние R184/R198/R204/R207/V83 artifacts. Один inboundDLQ message
существовал до rollout; общий health503/DOWN остаётся отдельным operational gate,
readinessUP. DLQ не consume/ack/purge/requeue. Migration/backfill/flags не запускались.

Следующий пакет — source read/configure identity, затем retrieval/handoff;
child/media/GENERAL/task/project/system policies/privacy/delivery/fleet/flags/S6
открыты, historical S2B снятR190, общий Task🟡. Staging/commit/push нет.
