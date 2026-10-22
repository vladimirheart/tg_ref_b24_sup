# 01-278 — R162: legacy participant audit

Дата: 2026-10-22. Общий статус задачи: 🟡.

## Исходный пользовательский промт

> давай ещё один примерно такой-же шаг

## Baseline и среда

Свежие HEAD/origin/main/actual remote: 039b0acccefc20ff3fd4623c1b9ff9d0bd5a9209.
Parent 8258d76612f97188c29c0e048143198b0e79a4fc, subject
«fix(multibusiness): изоляция legacy-отметок прочтения #ai». Проверены реальные
5 файлов R161, исходные main/index чистые. Detached sandbox .tmp/01-278-r162.

Рабочие Docker/проект находятся на другой машине, в другой сессии агента.
Здесь source/test checkout; местный Docker используется только для отдельного
PostgreSQL Testcontainers. Рабочая среда не инспектировалась и не менялась.
Grant пользователя R160 на commit/push сохраняется в этой же сессии только
2026-10-22 Europe/Moscow, до 2026-10-23 00:00 +03 / 2026-10-22 21:00 UTC.
Source apply и Git publisher разделены; срок проверяется перед Git mutation.

## Изменение

R162: узкий пакет одного audit writer. Raw participants_add/participants_remove
получили ту же UNRESOLVED/non-GENERAL/unique-root границу INSERT, что take/reassign.
Для всех четырёх action codes strict проверяется до lookup и повторно после него.
Поздний business/GENERAL/collision/missing proof не добавляет новый raw audit,
включая result=success/error/not_found. Исторические строки остаются без изменений;
opaque ticket ID, case/trim/default actor/result/detail и timestamp сохранены.
Другие audit actions, successful-action lookup и telemetry сохраняют прежний
контракт. Это не current READ для raw audit и не business/generation attribution.
Операции с участниками, их final writer/recipient policy этим не закрыты;
strict resampling не сериализует in-memory flag после второй проверки.
Минимальная source-схема V78; новых migrations и рабочих изменений нет.

В R160 raw take/reassign audit уже имел SQL namespace guard; два participant
action codes из DialogQuickActionsController его не использовали. Если корень
менял scope после раннего can_assign proof, поздний result мог попасть в raw
audit нового BUSINESS/GENERAL root. Теперь эти четыре audit action codes используют
общий список и тот же финальный SQL predicate. Строгий режим повторно проверяется
после LegacyTicketIdJdbcGuard, а не только до него. Поведение применяется ко всем
result status этих action codes; старая история не удаляется и не атрибутируется.
Пять SQL-параметров и их trim/default/case правила сохранены. Сами participant
INSERT/DELETE, текущие права чтения raw audit, другие audit/telemetry actions,
recipient policy и full owner/generation locking этим пакетом не переписывались.

## Валидация и точный scope

176 Java tests / 3 свежих Surefire XML GREEN, failures/errors/skips=0,
BUILD SUCCESS в .tmp/r162-panel-tests.log. Новый boundary suite — 50; прежний
audit snapshot suite — 117, service suite — 9. Golden assertions не менялись.
Проверены четыре action codes, BUSINESS/GENERAL/strict/collision/missing/schema,
поздние изменения после proof и после формирования INSERT, success/not_found/error,
opaque ID/trim-neighbour, сохранность historical rows и нормализованных полей.
Отдельно проверены strict без JDBC lookup и прежний unrelated action/lookup contract,
включая first_response_overdue_notification. Telemetry и её quirks покрыты старым suite.
XML/source freshness и compiled classes сверены перед source apply.

Точный guarded scope — 5 файлов: один production, один test, три документа.
Четыре Maven Sass CSS исключены по baseline blob/content proof. Корневой checkout
изменяется только после GREEN и exact baseline/file/content guards; Git — отдельной фазой.
Новой SQL migration нет, source minimum V78. Fleet/browser/live provider проверки,
рабочие migrations/backfill/config/flags/restart/deploy не выполнялись.

## Осталось

Final participant writers и current recipient authority, остальные legacy write/read
APIs, historical attribution, producer context, S2B согласование и сквозная приёмка.
Этот пакет ограничивает raw audit двух participant действий и не закрывает сами
операции с участниками или всю задачу 01-278.
