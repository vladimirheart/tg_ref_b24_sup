# 01-278 / R289 — разбор зависшей доставки запросов оценки

Промты пользователя:

> выполни 1-й пункт

Ответ на вопрос о живых проверках:

> тесты уже проводились ранее - всё было ок.

Дата:2026-11-05, Europe/Moscow +03.

## Source R289 — 2026-11-05: разбор delivery/unknown без повторной отправки

Промт: «выполни 1-й пункт» — доставка/оценки, restart и неизвестный исход.
На вопрос о живой проверке пользователь ответил: «тесты уже проводились ранее -
всё было ок.». Это принято как подтверждение прежних live delivery/rating checks;
дата/revision самих прогонов не сообщены. Здесь новые provider sends/live tests
и restart рабочего приложения не выполнялись; повтор не требуется ради R289.
Исторические R265 process-kill, R266 persisted rating и R267 cold fleet start
сохранены. Full in-flight restart всего штатного launcher/fleet не объявляется
проверенным: он остаётся в общей release/rollback приёмке S6.

Закрыт программный остаток пункта1: scripts/feedback-delivery-recovery.ps1 и
два scoped SQL дают read-only review одной exact platform/channel/user/event
попытки и явный reconcile только по committed положительной V85 receipt.
SHA256 обеих полных rows, repeatable-read/row locks и повторная проверка всего
ledger/receipt context запрещают stale proof/дубликат/чужую квитанцию. Меняются
только status/delivered_at из receipt/last_error/updated_at; receipt, outbox,
pending/rating, provider/API/Rabbit не затрагиваются. Без квитанции unknown
остаётся blocked; timeout/false/503 не дают права на resend. Retryable допускает
только отдельный штатный replay с новой авторизацией, утилита его не делает.

73 новых проверки GREEN на собственном native PostgreSQL16.15/psql16.6:
три платформы ×sending/uncertain, дубликат, unsafe statuses, missing/prepared/
cross-context receipt, stale snapshots, wrong scope, SQL rollback/native failure,
malformed input, Unicode/raw ID и BIGINT/Long.MAX_VALUE. Временный cluster
на fresh repo/.tmp/random loopback port остановлен; Docker не использовался.
Windows PowerShell5.1 self-test/parser проверены; SQL выполнялся настоящим psql.
Полная Docker-wrapper ветка с рабочим PostgreSQL здесь не запускалась.
Java/POM/schema/runtime config прежние, Maven/JVM regression не повторялись.

Baseline HEAD/origin/main/remoteae45b515d (R288), parent4a026df59;6 prior changed
paths/blob content и4098 tracked files проверены. Scope9: operator,2 SQL,test,
runbook,task/S0/BOT/new changelog. Evidence .tmp/r289-sql-green.json и retained
native cluster logs/proof; версии неуспешной подготовки Windows harness сохранены.
Инструкция: docs/runbooks/feedback-delivery-recovery.md. Оператор вызывается
владельцем на машине проекта с exact running PostgreSQL container ID; пароли,
.env/application YAML и Compose не читает, restart/rebuild для утилиты не нужен.
Root apply отдельно от staging/commit/push; рабочая машина/БД здесь не менялись.
Новых migrations/flags нет. R287:38 MAX PG/Rabbit cases по-прежнему PENDING_DOCKER.
Остальные isolation/policies/AI/первый production S6 остаются, задача🟡; S2B снятR190.

Изменённые файлы:

- `ai-context/changelog/2026-11-05_01-278_feedback_delivery_recovery_r289.md`
- `ai-context/rules/03-multibusiness-s0-contract.md`
- `ai-context/tasks/task-details/01-278.md`
- `docs/BOT_RUNTIME_CONTRACT.md`
- `docs/runbooks/feedback-delivery-recovery.md`
- `scripts/feedback-delivery-recovery.ps1`
- `scripts/sql/feedback-delivery-reconcile.sql`
- `scripts/sql/feedback-delivery-review.sql`
- `scripts/tests/feedback-delivery-recovery.tests.ps1`
