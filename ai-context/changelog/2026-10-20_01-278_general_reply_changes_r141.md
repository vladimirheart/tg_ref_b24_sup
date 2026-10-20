# 01-278 — Telegram GENERAL text edit/delete (R141)

Дата: 2026-10-20. Задача 01-278, статус 🟡.

Промт пользователя:

> продолжай

Рабочий Docker и проект находятся на другой локальной машине и в другой
сессии агента. Здесь source/tests и isolated PostgreSQL Testcontainers;
рабочие revision/DB/flags/сервисы не инспектировались/не менялись.

Fresh HEAD/origin/main/actual remote main 55b30a8eedab18c8489ef41608aa9d6dea5e75ae,
parent bbbfba6bb4bf4ee736c34659451fedbfaf4af7d2; actual R140 — 14 файлов.
Main clean; реализация/validation в detached sandbox.

Добавлены отдельные EDIT/DELETE формы и server path подтверждённых R136
Telegram GENERAL text replies. Current PAGE_DIALOGS/READ/OPERATE/account,
flags/status/exact owner/public generation и canonical history обязательны.
V71 хранит immutable command snapshot и audit; same-key claim и partial unique
index предотвращают duplicate/concurrent/ambiguous retries. Channel/provider/
content/work revision фиксируются до mutation; network boundary guard повторяет
допуск непосредственно перед HTTP. Provider success атомарно меняет canonical
history, command/audit и metadata-only GENERAL outbox. Исходная отправка не
переписывается; удаление скрывает canonical history, журнал сохраняется.
UNKNOWN/SENDING блокируют следующие команды до ручной сверки. Legacy API,
providers VK/MAX, media, grants/defaults/runtime flags не расширены.

Validation: 360/360 GREEN, 10 XML, failures/errors/skips=0.
GeneralTicketWorkHttpPostgresTest: 61; GeneralTicketReplyPostgresTest: 66; GeneralTicketRealtimePostgresTest: 38; GeneralTicketReadServicePostgresIntegrationTest: 7; PostgresRuntimeReadinessVerifierTest: 15; UiEventOutboxScopePostgresIntegrationTest: 16; UiEventOutboxWatcherTest: 12; GeneralTicketReplyChangePostgresTest: 90; DialogReplyTransportServiceTest: 23; DialogQuickActionServiceTest: 32.
Full Flyway V71 / V70→V71 preservation, actual Spring/Thymeleaf/CSRF,
same/different-key concurrency, grant locks/revocation и late changes,
four-stage post-provider rollback, real guarded HTTP/frozen payload,
no retry of UNKNOWN/SENDING; два Node harness и diff check GREEN.
Transform v1 остановлен до tracked source write на неуникальном anchor;
v2 использует exact signatures. Initial SQL run RED из-за FK/public-ID/token/business-column
fixtures и Mockito when, а также неверной missing-account simulation.
Fixtures исправлены без отключения constraints или ослабления assertions;
финально заново выполнены все 10 suites, 685 main / 507 test compiled.
Source operator v4; quiet log config только для test JVM. Финальные reports проверены.
Пакет 13 project files, SHA-256/exact-set guards, generated CSS исключён.

Staging/commit/push — ручные. Deploy, рабочие migrations/backfill/flags/config,
rebuild/restart и live provider/Redis/proxy acceptance не выполнялись.
Media, VK/MAX edit/delete, feedback, legacy attribution, автоназначение,
notifications и retention/reconciliation остаются PENDING; общий статус 🟡.
