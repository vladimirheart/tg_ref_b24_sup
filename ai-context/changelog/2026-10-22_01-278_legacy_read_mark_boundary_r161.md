# 01-278 — R161: legacy read-mark boundary

Дата: 2026-10-22. Статус всей задачи: 🟡.

## Исходный пользовательский промт

> давай дальше, но меньшим пакетом

## Baseline и среда

Свежие HEAD/origin/main/actual remote main: 8258d76612f97188c29c0e048143198b0e79a4fc.
Parent b1ab370d132a253de9317adc50fce70e0e152a6f; subject:
«fix(multibusiness): изоляция legacy-путей назначения ответственного #ai».
Проверены фактические 15 файлов R160, root/index чистые. Detached sandbox
.tmp/01-278-r161 создан от этого baseline. R160 не повторяется.

Рабочие Docker/проект находятся на другой машине, в другой сессии агента.
Здесь только source/test; местный Docker используется для отдельного PostgreSQL
Testcontainers. Рабочая среда не инспектировалась и не менялась. Разрешение
пользователя R160 на commit/push продолжает действовать в той же сессии только
2026-10-22 Europe/Moscow, до 2026-10-23 00:00 +03 / 2026-10-22 21:00 UTC.
Source apply и Git publisher разделены; publisher проверяет срок перед действиями.

## Изменение

R161: узкий пакет одного legacy writer — markDialogAsRead. Bare-ID read mark
пишется только в UNRESOLVED/non-GENERAL до strictReadScope, для точного stored
responsible. При strict ранний lookup не выполняется; UPDATE повторяет namespace,
unique root и получает актуальное strict значение при формировании аргументов.
BUSINESS/GENERAL, collision, missing root/schema не меняют last_read_at. Расчёт
MAX(chat_history.timestamp) / CURRENT_TIMESTAMP, точные ID, actor matching,
assignment fields и прежняя обработка закрытых legacy roots сохранены.
Это не новый scoped BUSINESS/GENERAL read-mark API, не исправление всей legacy
read authorization и не historical business/generation attribution child rows.
Source minimum остаётся V78; SQL migration и рабочие изменения отсутствуют.

Legacy DialogReadService вызывает этот writer перед формированием деталей/истории;
раньше global PAGE_DIALOGS и совпадение stored responsible позволяли менять
BUSINESS read mark через старый raw путь без current scope authority. Теперь
read-side effect ограничен compatibility namespace; сами ответы/reads не переписаны.
Новых grants/ролей, нормализации username/ticket ID или смены ответственного нет.
Strict проверяется перед lookup и передаётся в финальный UPDATE. SQL повторяет
namespace и unique-root proof; это не транзакционная сериализация изменения
in-memory feature flag после формирования аргументов и не заморозка generation.

## Валидация и точный scope

169 Java tests / 3 свежих Surefire XML, failures/errors/skips=0,
BUILD SUCCESS в .tmp/r161-panel-tests.log. LegacyReadMarkBoundaryPostgresTest — 24,
прежний snapshot suite — 139, прежний service suite — 6. Golden assertions не менялись.
Проверены BUSINESS/GENERAL/strict, collision/missing/null marker/missing schema,
поздние изменения после early proof и после формирования аргументов UPDATE,
opaque ID/trim-neighbour, неизменность assignment fields и strict без JDBC lookup.
Сохранены прежние MAX(timestamp) domain/fallback, actor quirks и duplicate/delete races.
XML/source freshness и compiled classes проверены перед source apply.

Guarded apply: 5 файлов — один production, один test, три документа. Четыре CSS,
созданные Maven Sass, исключены по baseline blob/content proof. Root меняется только
после GREEN и exact baseline/file/content guards; staging/commit/push отдельной фазой.
Новых SQL migrations нет, source minimum V78. Fleet/browser/live/provider проверки,
рабочие migrations/backfill/config/flags/restart/deploy не выполнялись.

## Осталось

Scoped BUSINESS/GENERAL read marks и historical attribution, остальные legacy
reply/lifecycle/participant/read APIs, producer context и сквозная приёмка/rollout.
Этот меньший пакет закрывает ровно один writer и не завершает задачу 01-278.
