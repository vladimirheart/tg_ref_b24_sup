# 01-278 — ручное создание обращения GENERAL (R140)

Дата: 2026-10-19. Задача 01-278, статус 🟡.

Промт пользователя:

> продолжай

Рабочий Docker и весь проект, по ранее принятому уточнению пользователя,
находятся на другой локальной машине и в другой сессии агента. Здесь исходники
и isolated tests; рабочие runtime revision/DB/flags не инспектировались/не менялись.

Fresh baseline HEAD/origin/main/actual remote main:
bbbfba6bb4bf4ee736c34659451fedbfaf4af7d2, parent
3f7e45d4b6fc6c16700c3bc7efc465d2aa445ee7. Actual R139: 14 files.
Основной checkout чистый, реализация/validation — detached worktree.

Добавлен manual GENERAL путь /general-dialogs/new и ссылка для current operator.
Нужны PAGE_DIALOGS/current READ+OPERATE, CSRF и три existing flags: foundation,
general-ticket-queue, manual-ticket-creation. Новый локальный клиент в отдельном
реестре получает отрицательный ID общей V54 sequence; бизнес-клиент не выбирается
и не импортируется. Root explicit GENERAL/NULL business/MANUAL, pending/revision 0,
без канала, локации и legacy spans. Форма явно сообщает об отсутствии доставки.

V70 расширяет только named scope CHECK через immutable source function и
создаёт local registry/immutable request audit. Old sources/roots сохраняются;
bot ingress allowlist, grants, defaults и runtime flags не менялись.
Actor/grants locks, namespaced same-key advisory lock и actor/input SHA-256
обеспечивают idempotency; replay требует current exact owner/root/registry.
Root recapture, changed input/actor, revoked rights, deleted root не создают дубль.
Client, message/ticket, operator_note history/public generation, request audit
и metadata GENERAL outbox атомарны. Нет provider/AI/legacy callbacks; work timer
начинается последующим status transition. Redirect выдаёт только public ID.
Обновлены S0/current readiness summary; задача не объявляется завершённой.

Validation: 389/389 GREEN / 12 XML без failures/errors/skips:
manual GENERAL 42, manual business 2, assignment 48, work 85, HTTP 50, reply 66,
realtime 38, reads 7, controllers 10, readiness 13, outbox scope 16, watcher 12.
Compile 684 main / 506 test; full Flyway до V70, upgrade V69→V70 preservation,
общий negative namespace, duplicate/concurrent keys, exact owner/input/actor,
six-stage rollback, current rights/flags, locking races, real CSRF и late changes.
При review fingerprint получил явные CAST text и regression default/unspecified
JDBC. Initial run прерван для правки и сохранён; final все suites повторены,
partial XML не засчитаны. Operator v2, report freshness, два Node harness и diff
check GREEN. Пакет 14 files, exact-set/SHA-256 guards; generated CSS исключён.

Staging/commit/push оставлены пользователю. Местный Docker — только isolated
Testcontainers. Рабочие migrations/backfill/flags/config, rebuild/restart/deploy
и live provider/Redis/proxy acceptance не выполнялись. Остальные GENERAL media/
feedback/legacy attribution/auto-assignment/notifications/retention и другие
блоки текущей сводки остаются PENDING. Source apply не равен приёмке runtime.
