# 01-278 — точные получатели в маршрутизации уведомлений, R174

Время: 2026-10-23 08:48:17 +03:00 (Europe/Moscow).

Промт пользователя:

> продолжай

Действующий grant из текущей сессии:

> Продолжай. разрешение на пуш и коммит расширяю на сегодняшний день

## Baseline и среда

Baseline R174: HEAD/origin/main/actual remote main
d8bcdca500e8570f57e8a5ebaad8761ca69f2b9b; parent
300a623402164124caad34ba85b44c7ffd5e0d58. R173 subject и фактические
15 файлов (557 additions/26 deletions) подтверждены; main/index исходно чистые.
Detached sandbox .tmp/01-278-r174. Рабочий экземпляр и Docker — на другой машине,
в другой сессии. Здесь source и изолированные PostgreSQL Testcontainers;
рабочие revision/DB/config/flags/providers не проверялись и не менялись.
Рабочие restart/rebuild/deploy/migration/backfill не выполнялись. Действует grant
пользователя текущей сессии на commit/push 2026-10-23 Europe/Moscow, expiry
2026-10-24 00:00 +03 (2026-10-23 21:00 UTC). Source apply и отдельный Git publisher
проверяют exact baseline/content/files/actual remote; publisher также expiry.
Разрешение не включает рабочий rollout; force push не используется.

## Изменение

R174: NotificationRoutingService сохраняет точные account keys без trim,
с прежним lower-case Locale.ROOT. Base recipients, DB usernames, typed JSON
employeeUsernames/excludeUsernames и сопоставление portal activity больше не
сливают plain/spaced accounts. Это важно для employees_only, department_all,
department_except, base/base_plus_route и online_only_fallback_all: состав и
активность конкретного account не переносятся похожему имени. Enabled/blocked,
department lookup, case dedup, disabled route, пустое сообщение и fallback при
отсутствии online recipients сохраняют прежний контракт.
Array element — точное имя account; отделённый legacy scalar text со списком
по comma/semicolon/newline сохраняет прежний явный trim отдельных tokens.
Blank elements исключаются. Department/event/scope/route labels и content не
становятся account keys и сохраняют свою нормализацию. Save/load, wrapped/direct
payload, top-level settings apply и HTTP settings сохраняют typed array names.
Изменён только этот production service; NotificationService/SSE predicates,
grant rules, default audiences, source minimum V81 и migration chain неизменны.
UI/JS/templates и рабочие настройки не менялись. Global/system/task policy,
AlertQueueService, ChannelAssignmentRoutingService, ChannelTransportService,
IncidentRouteDeliveryService user/freeform targets, legacy responsible/active/
spans recipient attribution и PermissionService остаются отдельными gates.
Этот шаг не вводит новую business-авторизацию системных уведомлений.

## Проверки

Проверки R174: 117 Java tests / 11 свежих XML GREEN,
failures/errors/skips=0, Maven exit 0, .tmp/r174-panel-v2.log. До исправления
production source был exact baseline blob: отдельный v1 дал 5/5 RED cases с
неверным plain recipient вместо leading/trailing/both spaces/tab/newline.
Original XML/log/source/test SHA — в ignored .tmp/r174-baseline-report.xml,
r174-panel-v1.log и r174-baseline-red.json. Тот же diagnostic source SHA
неизменен в итоговом GREEN; assertions не ослаблялись.
Новый PostgreSQL suite проверяет canonical DB directories двух qualifiers,
base sets, typed selectors/exclusions, чужой department, online activity,
fallback/case dedup, save/load/apply, legacy scalar text, disabled/blocked/
deleted/moved accounts и missing schema. Новый full Spring/JPA/Flyway HTTP suite
проверяет settings POST/GET с PAGE_SETTINGS и CSRF, перечитывание сохранённой
конфигурации, dispatch в реальную notifications row, точный SSE refresh target,
чтение/read-all только адресатом и повторный current directory lookup.
Существующие fixtures и production readiness/grants не изменялись. Source/class/
XML freshness и exact generated CSS baseline blobs сверены; Node/browser=0,
так как JS/templates неизменны. Live Redis/proxy/providers, рабочая functional
acceptance и rollout не выполнялись. Общий статус 01-278 остаётся 🟡.

## Передача

Guarded source apply переносит ровно 6 проверенных файлов после GREEN; Git
publisher отделён и повторяет exact baseline/content/files/actual remote и
expiry checks. Фактические source/commit/push/deployment состояния сохраняются
в ignored .tmp/r174-result.json и итоговом ответе. Рабочая среда — на другой
машине. Следующие gates перечислены выше; S2B/provider/fleet/retention и
рабочая приёмка остаются незавершёнными.
