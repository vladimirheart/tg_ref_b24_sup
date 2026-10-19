# 01-278 R137: ручное закрытие/reopen GENERAL и границы сессий

Checkpoint: 2026-10-19 14:32:06 MSK.

Промт пользователя: «репо обновлён. давай дальше. обрати внимание, что рабочий docker и проек в целом находится не в этой машине, а в другой и с другой сессией агента, о чём должно быть задокументировано».

Baseline HEAD/origin/main/remote main: c76fa5dafac9e080254e0e3a60282d0ef665256a,
parent ce42d6125dc2d9cb6724715dbb2aa904a667ab7e. Проверены fresh fetch,
чистый исходный checkout, message и реальные 16 файлов R136. Работа выполнена
в отдельном detached worktree. Scope — 12 project files, exact set в manual
commit script/validation manifest; generated CSS исключён.

Пользователь подтвердил: рабочий Docker и запущенный проект находятся на
другой локальной машине в другой сессии агента относительно source/test R137.
Исправлены актуальная сводка/environment задачи и S0; исторические R135/R136
наблюдения сохранены с привязкой к runtime-сессии, где были получены.
Production checkpoint rule теперь требует явной привязки evidence к среде/
сессии: одинаковый checkout path и слова «эта машина» не являются такой связью.
Actual runtime revision/DB/flags/rollback отсюда не проверены.

GeneralTicketWorkService и формы теперь поддерживают manual pending/open→closed
и closed→pending. Close фиксирует actor/time/count; reopen очищает resolution
и обновляет reopen count/time. PAGE_DIALOGS/current READ+OPERATE/CSRF, exact
tuple/public generation, unique raw ID, active canonical account и status/CAS
проверяются до атомарных status/metadata/counters/audit/GENERAL outbox writes.
Повтор/старая ABA форма получает conflict. Legacy lifecycle/feedback/client
delivery и assignment не вызываются. Reply form закрытого ticket скрыта.

V67 расширяет только allowed GENERAL work audit transitions, сохраняет старые
rows/grants/immutable audit. SQL function gate используется и финальной записью;
readiness требует V67. Missing schema/прав/owner или downstream failure закрывают
write без частичного результата. Старые migrations не меняются, backfill нет.
Close лишает прежний queued reply актуальной revision, reopen его не возрождает.
Уже guarded provider attempt завершается под своими locks до изменения статуса;
закрытие не отправляет и не повторяет клиентское сообщение.

Валидация: 243/243 GREEN / 9 свежих XML, failures/errors/skips=0:
work SQL 59, HTTP 30, reply SQL 66, realtime 36, reads 7, controllers 10,
readiness 7, outbox scope 16, watcher 12. Flyway V1–V67 и upgrade V66→V67,
preservation/transition CHECK matrix, rights/owner/generation, actual Cookie/XOR
forms, stale/ABA, rollback, queued cancellation и concurrent in-flight close
проверены. Compile 680 main / 504 test, два Node harness и diff check GREEN;
XML freshness сверена с final production Java/SQL/templates и собственной suite.

Initial run остановлен после ошибки V67: неверно назван автоматически созданный
multi-column CHECK. Его фактическое имя general_ticket_work_events_check
проверено в pg_constraint изолированного R137 PostgreSQL; исправлена только V67,
прежние migrations/constraints не ослаблялись. Весь targeted набор повторён,
failed initial log сохранён; partial reports не используются как final GREEN.
Guarded apply artifact выпущен как v2.

Только local named-pipe Docker и изолированные PostgreSQL Testcontainers;
provider network mocked. Рабочий Docker/Compose/DB другой машины не
инспектировались/не менялись; нет remote probes, deployment/migrations/backfill/
config/flags/rebuild/restart. Staging/commit/push остаются ручным шагом.
Browser/live Redis/proxy/provider E2E и рабочая приёмка не заявляются.
GENERAL resolve/feedback/activity/span, media/edit/delete/assignment/manual
creation, reconciliation/retention и остальные S2B/S3–S6 gates PENDING.
Задача 🟡, source apply не даёт разрешения включать strict scope/rollout.
