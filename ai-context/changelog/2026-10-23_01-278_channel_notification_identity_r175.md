# 01-278 — точные получатели канальных уведомлений, R175

Время: 2026-10-23 15:12:25 +03:00 (Europe/Moscow).

Промт пользователя:

> продолжай

Действующий grant текущей сессии:

> Продолжай. разрешение на пуш и коммит расширяю на сегодняшний день

## Baseline и среда

Baseline R175: HEAD/origin/main/actual remote main
6f6f4909d860da88362bf37aae1094f4318d25a2; parent
1f059a33265b37785a09df891a8e57bd48ff1d59. После R174 fast-forward сохранены два
новых UI-коммита задачи 01-281; subject/parent/фактические файлы проверены.
Последний UI commit содержит 10 файлов (499 additions/7 deletions); суммарно
два UI-коммита — 35 файлов (2140 additions/64 deletions). Main/index исходно чистые.
Detached sandbox .tmp/01-278-r175. Рабочий экземпляр и Docker находятся на другой
машине, в другой сессии; здесь source/test и изолированные PostgreSQL Testcontainers.
Рабочие revision/DB/config/flags/providers не проверялись и не менялись;
restart/rebuild/deploy/migration/backfill не выполнялись. Пользовательский grant
на commit/push действует 2026-10-23 Europe/Moscow до 2026-10-24 00:00 +03
(2026-10-23 21:00 UTC). Source apply и отдельный Git publisher повторяют exact
baseline/content/files/actual remote; publisher также expiry. Force push и
рабочий rollout в этот шаг не входят.

## Изменение

R175: AlertQueueService сохраняет точные account keys без trim при прежнем
lower-case Locale.ROOT в typed employeeUsernames/excludeUsernames, DB usernames,
online activity и передаче уже разрешённых assignment recipients. Канальный
селектор больше не подменяет spaced account похожим plain account другого
department/business и не переносит его online activity или исключение.
ChannelTransportService использует отдельный exact-key normalizer только для
notification arrays: panelNotifications.routing, direct panelNotifications и
legacy alertQueue сохраняются через API без обрезки account names, с case dedup
и исключением blank entries. Department labels, preview и времена сохраняют свою
нормализацию; event defaults, online fallback и disabled/blocked filters прежние.
Current READ/full frozen owner/raw uniqueness/GENERAL denial по-прежнему проверяет
NotificationService перед INSERT; новых grant rules или channel business policy нет.
Production scope — только AlertQueueService и notification arrays в
ChannelTransportService; source minimum V81, migration chain и UI/JS неизменны.

Не закрыты: assignment resolver/config/directory/final writer identity
(ChannelAssignmentRoutingService, assignment settings ChannelTransportService,
DialogParticipantService и DialogResponsibilityService), UI CSV parsing/round-trip,
IncidentRouteDeliveryService user/freeform targets, legacy responsible/active/spans
attribution и global/system/task/PermissionService policy. Проверка source также
подтвердила отдельный gate: ChannelApiController использует общий authenticated
доступ/CSRF, без PAGE_SETTINGS на CRUD methods; R175 не меняет эту policy.
Положительный exact recipient handoff не доказывает корректность upstream assignment.

## Проверки

Проверки R175: 200 Java tests / 10 свежих XML GREEN,
failures/errors/skips=0: 9 GREEN suites v3 (.tmp/r175-panel-v3.log) и полный
повтор HTTP suite v4 (.tmp/r175-panel-v4.log), итоговый Maven v4 exit 0.
Сохранённые source/class/XML hashes девяти suites совпадают с финальными.
До изменения оба
production source совпадали с baseline blobs: v1 воспроизвёл 5/5 failures для
leading/trailing/both spaces/tab/newline (errors/skips=0). Original XML и test SHA
сохранены в ignored .tmp/r175-baseline-report.xml и r175-baseline-red.json;
diagnostic source SHA неизменен в итоговом GREEN, assertions не ослаблялись.
Новый PostgreSQL suite проверяет DB department keys, typed selector/exclusion,
online activity и fallback, exact assignment handoff, три config shapes,
save/reload, current disabled/blocked/moved/deleted и frozen scope передачи.
Новый full Spring/JPA/Flyway HTTP suite проверяет channel PATCH, реальную
persisted config и dispatch до notifications row, точный mocked SSE target,
bell GET/read-all, foreign READ denial и late revoke/disable/block/archive/
duplicate/public-ID change/raw collision/GENERAL. Authentication и CSRF действуют.
Одна старая положительная AlertQueue fixture явно заменяет padded Sasha на
uppercase canonical SASHA; прежние assertions case dedup/получатели/preview
сохранены, а literal whitespace denial проверяет новый suite.
V2 остановился до тестов на двух лишних закрывающих скобках нового HTTP test;
v3 исправляет только эти скобки и повторяет все 10 suites. V2 log/exit/input hashes
сохранены. В v3 новый HTTP suite получил 3 fixture errors: immutable public ID,
unique ticket ID/channel и GENERAL provenance check. V4 меняет только подготовку
этих трёх состояний: valid DELETE/INSERT с новым public ID, raw collision с
другим/null channel и GENERAL с general_selection_source=MANUAL. Добавлены явные
проверки состояния БД; исходные delivery denial assertions сохранены. Production
triggers/constraints не отключались; v3 XML/source сохранены в ignored
r175-http-v3-report.xml и r175-http-v3.java. Все 18 HTTP tests повторены целиком.
Production classes скомпилированы в v2 с теми же source SHA и pinned class SHA;
классы девяти v3 GREEN suites совпадают побайтно с финальными, их XML неизменны.
Новые HTTP classes/XML свежие относительно v4.
Остальные выбранные suites неизменны. Source/class/XML freshness и exact generated
CSS baseline blobs сверены; Node/browser=0, поскольку JS/templates не менялись.
Live Redis/proxy/providers, рабочая functional acceptance и rollout не проверялись.
Общий статус 01-278 остаётся 🟡.

## Файлы и передача

- ai-context/changelog/2026-10-23_01-278_channel_notification_identity_r175.md
- ai-context/rules/03-multibusiness-s0-contract.md
- ai-context/tasks/task-details/01-278.md
- spring-panel/src/main/java/com/example/panel/service/AlertQueueService.java
- spring-panel/src/main/java/com/example/panel/service/ChannelTransportService.java
- spring-panel/src/test/java/com/example/panel/controller/ChannelNotificationIdentityHttpPostgresTest.java
- spring-panel/src/test/java/com/example/panel/service/AlertQueueServiceTest.java
- spring-panel/src/test/java/com/example/panel/service/ChannelNotificationIdentityPostgresTest.java

Guarded source apply переносит ровно 8 файлов после GREEN и завершается без
staging/commit/push. Отдельный publisher использует действующий суточный grant
и проверяет exact files/content/parent/actual remote/expiry, без force push.
Фактические source/commit/push/deployment состояния фиксируются в ignored
.tmp/r175-result.json и итоговом ответе; Git publication не означает deployment.
S2B, provider/fleet/retention и рабочая приёмка остаются незавершёнными.
