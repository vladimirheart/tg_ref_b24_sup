# 01-278 — точная identity SSE и уведомлений, R173

Время: 2026-10-23 08:17:56 +03:00 (Europe/Moscow).

Промт пользователя:

> продолжай

Действующее разрешение из текущей сессии:

> Продолжай. разрешение на пуш и коммит расширяю на сегодняшний день

## Baseline и среда

Baseline R173: HEAD/origin/main/actual remote main
300a623402164124caad34ba85b44c7ffd5e0d58; parent
8fe01c7a4cf28e3ad80316b894732345c919fa63. R172 subject и фактические
15 файлов (506 additions/16 deletions) подтверждены; main/index исходно чистые.
Detached sandbox .tmp/01-278-r173. Рабочий экземпляр и Docker находятся на другой
машине, в другой сессии; здесь source и изолированные PostgreSQL Testcontainers.
Рабочие revision/DB/config/flags/providers не проверялись и не менялись;
restart/rebuild/deploy/migration/backfill не выполнялись. Grant пользователя
23 октября на commit/push действует до 2026-10-24 00:00 +03 (2026-10-23 21:00 UTC),
не включает рабочий rollout. Source apply и Git publisher раздельные, с exact
baseline/content/files/actual remote и expiry guards; force push запрещён.

## Исправлено

R173: точная identity получателя SSE и уведомлений сохраняет whitespace при
прежнем lower-case Locale.ROOT. Исправлены четыре production service:
UiEventStreamService (connect/map key/targeted delivery/unregister),
UiEventTicketAccessService (current recipient READ SQL), UiEventFanoutPublisher
(Redis targetUser, Locale.ROOT) и NotificationService (authenticated list/page/
cursor/count/read/read-all, canonical recipient sets/exclusions/DB usernames).
Отдельный account с пробелами получает только свои права и уведомления; имена
с leading/trailing/both spaces, tab/newline не становятся соседним account.
Canonical roster использует numeric member ID/current username и final roster
proof; exact recipient не подменяется похожим именем после SQL lookup.
Current unique enabled/unblocked account, active business, membership либо
ALL_BUSINESSES READ, frozen full owner, raw uniqueness, late revoke, strict
UNRESOLVED и GENERAL exclusion сохранены. Новых grant rules, migration, flags,
UI/JS/templates нет; source minimum V81. Системные broadcast/heartbeat и прежние
blank fallbacks не менялись. Свободные legacy recipient lists по-прежнему
разбираются отдельным splitIdentities; их responsible/active/spans attribution
и NotificationRoutingService (base/config/DB audience), global/system/
PermissionService policy остаются следующими gates.

## Проверки

Проверки R173: 422 Java tests / 20 свежих XML GREEN,
failures/errors/skips=0, финальный Maven exit 0. Evidence: 15 сохранённых GREEN
suites v2 (.tmp/r173-panel-v2.log) + 5 полных rerun suites v4
(.tmp/r173-panel-v4.log), с exact source/classes/XML proof.
До исправления production-файлы были byte/blob-equivalent baseline: новый v1
дал 10/10 RED cases (5 recipient READ и 5 bell reads), failures=10/errors=0/
skips=0. XML/log/source SHA сохранены в ignored .tmp/r173-baseline-red.json и
r173-baseline-*.xml; отрицательные assertions не ослаблены.
Новый SSE suite использует реальные connect/emitter buffers, local fallback,
queued broadcast/target, publisher JSON и реальный subscriber с mock Redis,
две похожие реальные учётные записи разных бизнесов и late revoke/disable/
block/archive/duplicate. Новый notification HTTP suite использует полный
Spring/JPA/Flyway и действующие Security/CSRF filters; читает list/page/count,
проверяет read/read-all и неизменность полных notification rows при отказе,
canonical roster/exclusion и положительный собственный spaced account.
Шесть старых fixture suites явно обновлены под новый exact-name contract:
положительные owner/grant проверки используют uppercase canonical identity без
добавленных пробелов; targeted fanout assertion сохраняет literal spaces;
добавлен явный whitespace denial для четырёх READ-ролей. Прежние frozen-owner,
GENERAL/UNRESOLVED, current grants, pagination, outbox/roster assertions сохранены.
В v2 были 2 failures: старое ожидание /dialogs/<id> вместо уже канонического
/dialogs?ticketId=<id> (URL builder не менялся), и прежнее склеивание spaced
recipients в UNRESOLVED fixture. URL assertion уточнён, case dedup сохранён и
добавлена проверка разных notification IDs для plain/spaced recipients;
UNRESOLVED fixture теперь проверяет три точных имени вместо двух aliases.
Кроме того, новый HTTP fixture оставлял свои root rows без messages, и следующий
shared Spring context корректно отказался READY (16+8 errors). Добавлена очистка
собственных notification/roster/root rows после assertions. Production readiness
не отключалась, READ/owner predicates не менялись. Все пять затронутых suites
повторены целиком; оставшиеся 15 GREEN suites и production classes неизменны.
Вспомогательный v3 повтор остановлен до test execution из-за EOL guard
fixture operator: raw SHA сравнивался после LF normalization. Operator v4
проверяет raw bytes до normalization; основной checkout не менялся.
Source/class/XML freshness и exact generated CSS baseline blobs сверены.
Node/browser checks=0 (JS/templates неизменны). Live Redis/proxy/provider и
рабочая functional acceptance не выполнялись; задача 01-278 остаётся 🟡.

## Передача

Guarded apply переносит ровно 15 проверенных файлов после GREEN; отдельный
Git publisher проверяет exact content/file set, текущий remote и срок grant.
Фактические source/commit/push/deployment состояния фиксируются в ignored
.tmp/r173-result.json и итоговом ответе. Рабочая среда на другой машине.
Следующий аудит: legacy recipient attribution и global/system identity/policy;
остальные S2B/provider/fleet/retention/rollout gates задачи сохранены.
